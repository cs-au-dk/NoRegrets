// The amount has been fine tuned to cause a crash if the heap size hasn't been
// set to something else than the default (1.4Gb)
var amount = 30000000;
console.log("Allocating " + amount + " bytes");
var last = [];
for(var i = 0; i < amount; i++) {

    last = [last];

    if(i%10000000 == 0) {
        console.log(i);
    }
}
var idx = Math.floor(Math.random()*i);
while(idx-- > 0) {
    last = last[0];
}
console.log(last);