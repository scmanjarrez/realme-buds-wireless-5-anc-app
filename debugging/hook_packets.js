/**
 * Frida hook that logs the packets exchanged by the vendor app on the "tl" protocol.
 *
 * Each packet is printed as one JSON line with: dir ("TX" or "RX"), t (Date.now() in ms),
 * cmd (command id as 0x + 4 hex digits), seq, data (payload as hex) and stack (app call stack,
 * only for TX).
 *
 * Frame layout, offsets from the start of the frame (details in docs/PROTOCOL.md, "Frame anatomy"):
 *   0 aa start | 1 frameLength (total - 2) | 2-3 header | 4-5 cmd (u16 LE) | 6 seq |
 *   7-8 dataLength (u16 LE) | 9.. data
 *   frameLength is a varint of 1 or 2 bytes; the layout above is the one-byte form
 *   (with two bytes every later field shifts by one).
 * The hook reads the fields of the app's Packet object (cmd, payload and seq), not the raw bytes.
 *
 * Run it with the command below (see the comment at the top of the file for the process name):
 *   frida -U -n "com.realme.link:link.monitor" -l hook_packets.js
 */

// Logs every Packet of the "tl" protocol (the RFCOMM channel that carries the control commands),
// together with the app's call stack for outgoing packets.
//
// The protocol runs in a separate process, not the main one. Attach to it by name or PID:
//   frida -U -n "com.realme.link:link.monitor" -l hook_packets.js
// (list processes with `frida-ps -U | grep -i realme`)

const PKT = 'com.realme.iot.headset.tl.protocol.packet.Packet';

/**
 * Converts a Java byte array to a lowercase hex string, two digits per byte, no separators.
 *
 * @param {Array<number>|null} bytes - Byte array of the payload; null gives an empty string.
 * @returns {string} The bytes as hex, for example "aa0700".
 */
function hex(bytes) {
    if (bytes === null) return '';
    let s = '';
    for (let i = 0; i < bytes.length; i++) s += ('0' + (bytes[i] & 0xff).toString(16)).slice(-2);
    return s;
}

/**
 * Captures the current Java call stack and keeps only the app's own frames.
 *
 * Lines containing "com.realme" are kept, except lines of the Packet class itself. Up to six
 * lines are returned, trimmed and joined with " <- ".
 *
 * @returns {string} The filtered stack trace, or an empty string if no line matches.
 */
function appStack() {
    const Log = Java.use('android.util.Log');
    const Ex = Java.use('java.lang.Exception');
    return Log.getStackTraceString(Ex.$new()).split('\n')
        .filter(l => l.indexOf('com.realme') !== -1 && l.indexOf('Packet.') === -1)
        .slice(0, 6).map(l => l.trim()).join(' <- ');
}

/**
 * Prints one packet as a JSON line on the Frida console.
 *
 * The command id is read with p.f() (bit 15 set means response), the payload with p.e() and
 * the sequence number with p.i().
 *
 * @param {string} dir - "TX" for a packet being serialized to send, "RX" for a parsed received packet.
 * @param {Object} p - The Java Packet instance (this inside the hooks below).
 * @returns {void}
 */
function dump(dir, p) {
    const cmd = p.f();            // command id (bit 15 set = response)
    const data = p.e();           // payload
    console.log(JSON.stringify({
        dir: dir,
        t: Date.now(),
        cmd: '0x' + ('0000' + (cmd & 0xffff).toString(16)).slice(-4),
        seq: p.i(),
        data: hex(data),
        stack: dir === 'TX' ? appStack() : ''
    }));
}

/**
 * Installs the TX and RX hooks on the Packet class once the Java runtime is ready.
 *
 * @returns {void}
 */
Java.perform(function () {
    const Packet = Java.use(PKT);

    /**
     * Replaces the no-argument serializer of Packet. Logs the packet, then calls the original.
     *
     * @returns {*} The return value of the original serializer.
     */
    // TX: the packet is serialized right before it is sent
    Packet.a.overload().implementation = function () {
        dump('TX', this);
        return this.a();
    };

    /**
     * Replaces the constructor that takes a byte array. Runs the original constructor, then logs the packet.
     *
     * @param {Array<number>} b - The received bytes of one frame.
     * @returns {void}
     */
    // RX: the constructor that parses received bytes
    Packet.$init.overload('[B').implementation = function (b) {
        this.$init(b);
        dump('RX', this);
    };

    console.log('[+] hooks ready on ' + PKT);
});
