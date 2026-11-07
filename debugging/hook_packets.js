// Logs every Packet of the "tl" protocol (the RFCOMM channel that carries the control commands),
// together with the app's call stack for outgoing packets.
//
// The protocol runs in a separate process, not the main one. Attach to it by name or PID:
//   frida -U -n "com.realme.link:link.monitor" -l hook_packets.js
// (list processes with `frida-ps -U | grep -i realme`)

const PKT = 'com.realme.iot.headset.tl.protocol.packet.Packet';

function hex(bytes) {
    if (bytes === null) return '';
    let s = '';
    for (let i = 0; i < bytes.length; i++) s += ('0' + (bytes[i] & 0xff).toString(16)).slice(-2);
    return s;
}

function appStack() {
    const Log = Java.use('android.util.Log');
    const Ex = Java.use('java.lang.Exception');
    return Log.getStackTraceString(Ex.$new()).split('\n')
        .filter(l => l.indexOf('com.realme') !== -1 && l.indexOf('Packet.') === -1)
        .slice(0, 6).map(l => l.trim()).join(' <- ');
}

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

Java.perform(function () {
    const Packet = Java.use(PKT);

    // TX: the packet is serialized right before it is sent
    Packet.a.overload().implementation = function () {
        dump('TX', this);
        return this.a();
    };

    // RX: the constructor that parses received bytes
    Packet.$init.overload('[B').implementation = function (b) {
        this.$init(b);
        dump('RX', this);
    };

    console.log('[+] hooks ready on ' + PKT);
});
