"use strict";

var HeliumSelection = function () {

    var HANDLE_LEFT_URL = 'file:///android_res/drawable/ic_text_select_handle_left.png';

    var HANDLE_RIGHT_URL = 'file:///android_res/drawable/ic_text_select_handle_right.png';

    var HANDLE_WIDTH = 44;
    var HANDLE_HEIGHT = 22;

    var HANDLE_MARGIN = 11;

    /** X of last touch */
    var lastX;

    /** X of last touch */
    var lastY;

    var isInitialSelect;

    var anchorRange;
    var lastTouchPointRange;
    var lastWordRange;
    var goingForward;

    var inPageHotZone;

    var handleLeft;
    var handleRight;

    var movingHandle;
    var handleTouchDiffX;
    var handleTouchDiffY;

    function init() {
        handleLeft = createHandleElement(false);
        handleRight = createHandleElement(true);

        document.body.appendChild(handleLeft);
        document.body.appendChild(handleRight);
    }

    function onLongPress() {
        var range = document.caretRangeFromPoint(lastX, lastY);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        sel.modify('move', 'backward', 'word');
        sel.modify('extend', 'forward', 'word');
        isInitialSelect = true;
        lastTouchPointRange = null;
        goingForward = true;
        lastWordRange = null;
        anchorRange = cloneRange(sel.getRangeAt(0));
        console.log('long press');
    }

    function onTouchStart(e) {
        var x = e.touches[0].clientX;
        var y = e.touches[0].clientY;
        lastX = x;
        lastY = y;

        if (isInHandle(x, y, handleLeft)) {
            e.preventDefault();
            startMovingHandle(handleLeft, x, y);
        } else if (isInHandle(x, y, handleRight)) {
            e.preventDefault();
            startMovingHandle(handleRight, x, y);
        }
    }

    function onTouchMove(e) {
        if (isInitialSelect) {
            e.preventDefault();
            handleInitialMove(e);
        } else if (movingHandle != null) {
            e.preventDefault();
            handleHandleMove(e);
        } else {
            var range = document.caretRangeFromPoint(e.touches[0].clientX, e.touches[0].clientY);
            console.log('start = ' + range.startContainer + ':' + range.startOffset);
        }
    }

    function onTouchEnd(e) {
        console.log('end');
        if (isInitialSelect) {
            isInitialSelect = false;
            startStandby();
        } else if (movingHandle != null) {
            movingHandle = null;
        }
    }

    function handleInitialMove(e) {
        var x = e.touches[0].clientX;
        var y = e.touches[0].clientY;
        moveFocusTo(x, y);
    }

    function moveFocusTo(x, y) {
        var range = document.caretRangeFromPoint(x, y);
        var sel = window.getSelection();
        var rangeIsForwards = range.compareBoundaryPoints(Range.START_TO_START, anchorRange) > 0;

        var hotZoneSize = 48;
        var posInNextHotZone = x > window.innerWidth - hotZoneSize && y > window.innerHeight - hotZoneSize;
        var posInPrevHotZone = x < hotZoneSize && y < hotZoneSize;
        if (posInNextHotZone || posInPrevHotZone) {
            if (!inPageHotZone) {
                inPageHotZone = true;
                if (posInNextHotZone) {
                    HeliumApp.nextPage();
                } else {
                    HeliumApp.prevPage();
                }
            }
        } else {
            if (inPageHotZone) {
                inPageHotZone = false;
            }
        }

        if (lastTouchPointRange != null) {
            var res = range.compareBoundaryPoints(Range.START_TO_START, lastTouchPointRange);
            var relForward = rangeIsForwards ? 1 : -1;
            if (res == relForward && !goingForward) {
                goingForward = true;
            } else if (res == -relForward && goingForward) {
                goingForward = false;
            }
        }

        sel.removeAllRanges();
        sel.addRange(anchorRange);

        if (!rangeIsForwards) {
            // anchorRange was created with the start point being the start
            // of the word, so if the range is going backwards, then we need to
            // move it to the end of word.
            sel.collapseToEnd();
        }

        sel.extend(range.startContainer, range.startOffset);

        if (goingForward) {
            if (rangeIsForwards) {
                sel.modify('extend', 'backward', 'character');
                sel.modify('extend', 'forward', 'word');
            } else {
                sel.modify('extend', 'forward', 'character');
                sel.modify('extend', 'backward', 'word');
            }
        }

        lastTouchPointRange = cloneRange(range);
    }

    function startStandby() {
        console.log('enter standby');
        updateHandles();
    }

    function updateHandles() {
        var sel = window.getSelection();
        var range = sel.getRangeAt(0);
        var rects = range.getClientRects();
        if (!rects) {
            console.log('no rects?');
            return;
        }
        var first = rects[0];
        var last = rects[rects.length - 1];

        setHandlePosition(handleLeft, first.left - HANDLE_WIDTH + HANDLE_MARGIN, first.bottom);
        setHandlePosition(handleRight, last.right - HANDLE_MARGIN, last.bottom);
    }

    function startMovingHandle(handle, x, y) {
        movingHandle = handle;

        var sel = window.getSelection();
        var range = sel.getRangeAt(0);
        var rects = range.getClientRects();
        if (!rects) {
            console.log('no rects?');
            return;
        }
        var first = rects[0];
        var last = rects[rects.length - 1];

        if (handle == handleLeft) {
            handleTouchDiffX = x - first.left;
            handleTouchDiffY = y - (first.bottom + first.top) / 2;
        } else {
            handleTouchDiffX = x - last.right;
            handleTouchDiffY = y - (last.bottom + last.top) / 2;
        }

        var startRange = cloneRange(range);
        startRange.collapse(true);
        var endRange = cloneRange(range);
        endRange.collapse(false);

        var rangeIsForwards = endRange.compareBoundaryPoints(Range.START_TO_START, anchorRange) > 0;
        console.log('isforward: ' + rangeIsForwards);

        if (handle == handleLeft && rangeIsForwards) {
            console.log('swap1');
            anchorRange = endRange;
        } else if (handle == handleRight && !rangeIsForwards) {
            console.log('swap2');
            anchorRange = startRange;
        }
    }

    function handleHandleMove(e) {
        var x = e.touches[0].clientX - handleTouchDiffX;
        var y = e.touches[0].clientY - handleTouchDiffY;
        moveFocusTo(x, y);
        updateHandles();
    }

    function createHandleElement(isRight) {
        var element = document.createElement('div');
        element.style.position = 'fixed';
        element.style.padding = '0px !important';
        element.style.margin = '0px !important';
        element.style.width = HANDLE_WIDTH + 'px';
        element.style.height = HANDLE_HEIGHT + 'px';
        element.style.background = 'url(' + (isRight ? HANDLE_RIGHT_URL : HANDLE_LEFT_URL) + ')';
        element.style.backgroundSize = '100% 100%';
        element.style.pointerEvents = 'none';
        return element;
    }

    function setHandlePosition(handle, x, y) {
        handle.style.left = x + 'px';
        handle.style.top = y + 'px';
        handle.dataset.x = x;
        handle.dataset.y = y;
    }

    function isInHandle(x, y, handle) {
        var handleX = parseFloat(handle.dataset.x);
        var handleY = parseFloat(handle.dataset.y);
        return x > handleX && x < handleX + HANDLE_WIDTH && y > handleY && y < handleY + HANDLE_HEIGHT;
    }

    function cloneRange(range) {
        var out = document.createRange();
        out.setStart(range.startContainer, range.startOffset);
        out.setEnd(range.endContainer, range.endOffset);
        return out;
    }

    document.addEventListener('DOMContentLoaded', init);

    document.addEventListener('touchstart', onTouchStart, { passive: false });

    document.addEventListener('touchend', onTouchEnd);
    document.addEventListener('touchcancel', onTouchEnd);

    document.addEventListener('touchmove', onTouchMove, { passive: false });

    return { onLongPress: onLongPress };
}();
