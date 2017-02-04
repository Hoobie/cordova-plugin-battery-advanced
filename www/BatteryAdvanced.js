const cordova = require('cordova');

window.checkBatteryStatus = function(callback) {
    cordova.exec(callback, function(err) {
        callback(err);
    }, "BatteryAdvanced", "check", []);
};
