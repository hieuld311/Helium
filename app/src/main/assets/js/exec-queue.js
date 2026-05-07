"use strict";

/* jshint evil: true */

/**
 * This is a very very bad and very inefficient way to execute JS from Java.
 *
 * Native WebView provides a way to execute JS, but it's only available on Android 4.4+. Below
 * that, apps usually resort to loading a javascript scheme URL. Unfortunately, this causes a bug
 * with selection.
 */

var HeliumExecQueue = function () {

    /**
     * How often in ms we should check for new items.
     */
    var POLL_INTERVAL = 100;

    function start() {
        setInterval(check, POLL_INTERVAL);
    }

    function check() {
        // The native object may not be defined yet if the JS bridge is emulated.
        if (!liNativeExecQueue) {
            return;
        }
        var items = JSON.parse(liNativeExecQueue.getPendingItems());
        for (var i = 0; i < items.length; ++i) {
            try {
                eval(items[i]);
            } catch (e) {
                console.error("Error in exec queue:");
                console.error(e.stack);
            }
        }
    }

    start();
}();