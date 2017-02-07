const cordova = require('cordova');

window.startPowerMeasurements = function (callback) {
    cordova.exec(callback, function (err) {
        callback(err);
    }, "BatteryAdvanced", "start", []);
};

window.stopPowerMeasurements = function (callback) {
    cordova.exec(callback, function (err) {
        callback(err);
    }, "BatteryAdvanced", "stop", []);
};
