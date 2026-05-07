"use strict";

var HeliumAnnotations = function () {

    /** How often in ms the tracker should check the selection when nothing is selected. */
    var SELECTION_TRACKER_STANDBY_INTERVAL = 100;

    /** How often in ms the tracker should check the selection when the user is selecting. */
    var SELECTION_TRACKER_ACTIVE_INTERVAL = 50;

    /**
     * How long it takes in ms after a selection has been changed for it to be considered settled
     * after it's stopped moving.
     */
    var SELECTION_TRACKER_SETTLE_TIMEOUT = 300;

    /** The text selection dark alpha used when only selecting text. */
    var DEFAULT_SELECTION_ALPHA_DARK = 0.2;

    /** The text selection light alpha used when only selecting text. */
    var DEFAULT_SELECTION_ALPHA_LIGHT = 0.3;

    var RESTORE_FLAG_UPGRADE_19 = 1;

    /**
     * The alpha channel to be used for text selection and annotations.
     *
     * Note: Chrome replaces any alpha applied to ::selection that's above 0.996 with it's own
     * selection alpha.
     */
    var COLOR_ALPHA = 0.7;

    /** The class name temporarily applied to annotations. */
    var ANNOTATION_TMP_CLASS_NAME = "__HeliumAnnotation";

    var NOTE_MARKER_WIDTH = 38;
    var NOTE_MARKER_HEIGHT = 38;

    var NOTE_MARKER_ALPHA = 0.7;
    var NOTE_MARKER_ANIMATION_DURATION = 100;

    var TYPE_HIGHLIGHT = 0;
    var TYPE_UNDERLINE = 1;

    var trackerTimer = void 0;

    // Selection properties.
    var useLightDefaultSelectionColor = void 0;
    var lastSelectionRange = void 0;
    var isSelectionMoving = void 0;
    var selectionSettleTimer = void 0;
    var selectionStyleElement = void 0;
    var selectionColor = void 0;

    // Annotation applier properties.
    var classApplier = void 0;
    var highlighter = void 0;
    var trackedElements = void 0;

    var noteMarkerIdMap = {};
    var noteMarkersHidden = false;

    function init() {
        rangy.init();

        selectionStyleElement = document.createElement('style');
        selectionStyleElement.setAttribute('type', 'text/css');
        selectionStyleElement.dataset.excludeFromFootnote = true;
        document.head.appendChild(selectionStyleElement);

        trackedElements = [];

        classApplier = rangy.createClassApplier(ANNOTATION_TMP_CLASS_NAME);
        classApplier.useExistingElements = false;

        highlighter = rangy.createHighlighter();
        highlighter.addClassApplier(classApplier, { normalize: false });

        setSelectionColor(0);

        document.addEventListener('click', onClick);
        var _arr = ['touchstart', 'mousedown'];
        for (var _i = 0; _i < _arr.length; _i++) {
            var event = _arr[_i];
            document.addEventListener(event, function () {
                if (!trackerTimer) {
                    startSelectionTracker(SELECTION_TRACKER_STANDBY_INTERVAL);
                }
            });
        }
        var _arr2 = ['touchend', 'touchcancel', 'mouseup'];
        for (var _i2 = 0; _i2 < _arr2.length; _i2++) {
            var _event = _arr2[_i2];
            document.addEventListener(_event, function () {
                if (isRangeEmpty(getSelRange())) {
                    resetSelectionTracker();
                }
            });
        }
    }

    function startSelectionTracker(intervalMs) {
        if (trackerTimer) {
            clearInterval(trackerTimer);
        }
        trackerTimer = setInterval(checkSelection, intervalMs);
    }

    function resetSelectionTracker() {
        clearInterval(trackerTimer);
        trackerTimer = null;
    }

    /**
     * Gets the current selection range, or null if none exists.
     */
    function getSelRange() {
        var selection = window.getSelection();
        return selection.rangeCount ? selection.getRangeAt(0) : null;
    }

    function checkSelection() {
        var range = getSelRange();
        if (!rangeEquals(range, lastSelectionRange)) {
            clearInterval(selectionSettleTimer);

            if (!isRangeEmpty(range)) {
                if (isRangeEmpty(lastSelectionRange)) {
                    liNativeAnnotations.onSelectionCreated();
                    startSelectionTracker(SELECTION_TRACKER_ACTIVE_INTERVAL);
                }

                if (!isSelectionMoving) {
                    isSelectionMoving = true;
                    liNativeAnnotations.onSelectionStarted();
                }

                selectionSettleTimer = setTimeout(settleSelection, SELECTION_TRACKER_SETTLE_TIMEOUT);
            } else if (!isRangeEmpty(lastSelectionRange)) {
                liNativeAnnotations.onSelectionDestroyed();
                resetSelectionTracker();

                if (selectionColor && liNativeAnnotations.shouldInsertAnnotation()) {
                    insertAnnotation(lastSelectionRange, selectionColor);
                }
            }

            lastSelectionRange = range;
        }
    }

    function settleSelection() {
        isSelectionMoving = false;

        var rect = getSelectionBounds(lastSelectionRange);
        liNativeAnnotations.onSelectionStopped(rect.left, rect.top, rect.right, rect.bottom, lastSelectionRange.toString());
    }

    function isRangeEmpty(range) {
        return range == null || range.collapsed;
    }

    function rangeEquals(a, b) {
        if (a == null && b == null) return true;
        if (a == null && b != null) return false;
        if (a != null && b == null) return false;
        return a.startContainer == b.startContainer && a.startOffset == b.startOffset && a.endContainer == b.endContainer && a.endOffset == b.endOffset;
    }

    function setSelectionColor(color) {
        selectionColor = color;

        var visualColor = color;
        var alpha = COLOR_ALPHA;
        if (visualColor == 0 || visualColor == -1) {
            visualColor = useLightDefaultSelectionColor ? 0xFFFFFF : 0;
            alpha = useLightDefaultSelectionColor ? DEFAULT_SELECTION_ALPHA_LIGHT : DEFAULT_SELECTION_ALPHA_DARK;
        }
        selectionStyleElement.innerText = '::selection { background-color: ' + getCssRgbaStatement(visualColor, alpha) + ' }';

        if (!isRangeEmpty(getSelRange())) {
            forceRepaint();
        }
    }

    /**
     * Gets a CSS rgba() statement using the specified RGB color.
     *
     * @param color Color in RGBA format.
     * @param alpha Alpha from 0.0 to 1.0.
     * @return String containing rgba() statement.
     */
    function getCssRgbaStatement(color, alpha) {
        var r = color >> 16 & 0xFF;
        var g = color >> 8 & 0xFF;
        var b = color & 0xFF;
        var a = alpha;
        return 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')';
    }

    function getRangyForRange(rng) {
        var r = rangy.createRange();
        r.setStart(rng.startContainer, rng.startOffset);
        r.setEnd(rng.endContainer, rng.endOffset);
        return r;
    }

    function insertAnnotation(range, color) {
        var ranges = [getRangyForRange(range)];
        var text = range.toString();
        var highlights = highlighter.highlightRanges(ANNOTATION_TMP_CLASS_NAME, ranges);
        var serializedRange = JSON.stringify({
            'start': highlights[0].characterRange.start,
            'end': highlights[0].characterRange.end
        });
        highlighter.highlights = [];

        var elements = getNewElements();
        if (elements.length == 0) {
            console.log('Annotation not added.');
            return;
        }

        var result = JSON.parse(liNativeAnnotations.insertAnnotation(serializedRange, text, color));
        setupAnnotationElements(elements, result.id, result.type, color, result.hasNotes);
    }

    function restoreAnnotation(id, type, serializedRange, color, hasNotes, flags) {
        var rangeObj = void 0;
        try {
            rangeObj = JSON.parse(serializedRange);
        } catch (e) {}
        // Handled later.


        // Assume legacy annotation using stored Rangy serializations.
        if (!rangeObj) {
            // Rangy format: type:textContent|start$end$id$class$containerId
            var parts1 = serializedRange.split('|');
            var parts2 = parts1[1].split('$');
            rangeObj = { 'start': parts2[0], 'end': parts2[1] };
        }

        highlighter.deserialize(getRangySerializedString(rangeObj));
        highlighter.highlights = [];

        var elements = getNewElements();
        setupAnnotationElements(elements, id, type, color, hasNotes);

        if ((flags & RESTORE_FLAG_UPGRADE_19) != 0) {
            var text = "";
            for (var k = 0; k < elements.length; ++k) {
                var element = elements[k];
                text += (k > 0 ? ' ' : '') + element.innerText;
            }

            console.log('upgradeAnnotation19: ' + id);
            liNativeAnnotations.upgradeAnnotation19(id, text);
        }
    }

    /**
     * Translates our JSON range to a Rangy compatible serialized string.
     */
    function getRangySerializedString(rangeObj) {
        return 'type:textContent|' + rangeObj.start + '$' + rangeObj.end + '$' + 0 + '$' + ANNOTATION_TMP_CLASS_NAME + '$';
    }

    function getElementHighlightId(element) {
        return parseInt(element.dataset.highlightId);
    }

    function setupAnnotationElements(elements, id, type, color, hasNotes) {
        var _loop = function _loop() {
            var element = elements[_i3];
            element.classList.remove(ANNOTATION_TMP_CLASS_NAME);
            element.style.setProperty('display', 'inline', 'important');
            element.style.setProperty('padding', '0', 'important');
            element.style.setProperty('margin', '0', 'important');
            element.style.setProperty('font-size', 'inherit', 'important');
            element.style.setProperty('color', 'inherit', 'important');
            element.style.setProperty('text-shadow', 'none', 'important');
            element.dataset.highlightId = id;
            element.onclick = function (e) {
                var parent = element.parentNode;
                while (parent) {
                    var parentTagName = parent.tagName ? parent.tagName.toLowerCase() : null;
                    if (parentTagName === 'a') {
                        // Ignore clicks for elements embedded in links.
                        return;
                    }
                    parent = parent.parentNode;
                }
                var id = getElementHighlightId(element);

                var bounds = getAnnotationBounds(id);
                var text = "";
                for (var k = 0; k < elements.length; ++k) {
                    var _element = elements[k];
                    text += (k > 0 ? ' ' : '') + _element.innerText;
                }

                if (!isRangeEmpty(lastSelectionRange)) {
                    checkSelection();
                }

                liNativeAnnotations.onAnnotationSelected(id, bounds.left, bounds.top, bounds.right, bounds.bottom, text);

                e.preventDefault();
                e.stopPropagation();
            };
        };

        for (var _i3 = 0; _i3 < elements.length; _i3++) {
            _loop();
        }
        updateAnnotation(id, type, color, hasNotes);
    }

    function getAnnotationBounds(id) {
        var elements = getElementsForAnnotation(id);

        var unfilteredRects = [];
        for (var _i4 = 0; _i4 < elements.length; _i4++) {
            var e = elements[_i4];
            var _arr3 = e.getClientRects();

            for (var _i5 = 0; _i5 < _arr3.length; _i5++) {
                var r = _arr3[_i5];
                unfilteredRects.push(r);
            }
        }
        var rects = filterRectsOnScreen(unfilteredRects);

        if (rects.length == 0) {
            rects = unfilteredRects;
        }

        var left = Math.min(rects[0].left, rects[rects.length - 1].right);
        var top = rects[0].top;
        var right = Math.max(rects[0].left, rects[rects.length - 1].right);
        var bottom = rects[rects.length - 1].bottom;

        return { 'left': left, 'top': top, 'right': right, 'bottom': bottom };
    }

    function filterRectsOnScreen(rects) {
        var out = [];
        var width = window.innerWidth;
        var height = window.innerHeight;
        for (var _i6 = 0; _i6 < rects.length; _i6++) {
            var rect = rects[_i6];
            if (rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= height) {
                out.push(rect);
            }
        }
        return out;
    }

    function getNewElements() {
        // Slice the HTMLCollection so we get a real array, else the collection will update as we're
        // processing it later on.
        var elements = Array.prototype.slice.call(document.getElementsByClassName(ANNOTATION_TMP_CLASS_NAME));
        trackedElements.push.apply(trackedElements, elements);
        return elements;
    }

    function getElementsForAnnotation(id) {
        var elements = [];
        for (var _i7 = 0; _i7 < trackedElements.length; _i7++) {
            var _element2 = trackedElements[_i7];
            if (getElementHighlightId(_element2) == id) {
                elements.push(_element2);
            }
        }
        return elements;
    }

    function updateAnnotation(id, type, color, hasNotes) {
        var elements = getElementsForAnnotation(id);
        var backgroundColor = type == TYPE_HIGHLIGHT && color != -1 ? getCssRgbaStatement(color, COLOR_ALPHA) : 'transparent';
        var boxShadow = type == TYPE_UNDERLINE ? 'inset 0 -2px 0 0 ' + ('#' + color.toString(16)) : 'none';
        for (var _i8 = 0; _i8 < elements.length; _i8++) {
            var _element3 = elements[_i8];
            _element3.style.setProperty('background-color', backgroundColor, 'important');
            _element3.style.setProperty('box-shadow', boxShadow, 'important');
        }

        var noteMarker = noteMarkerIdMap[id];
        if (hasNotes && !noteMarker) {
            noteMarkerIdMap[id] = createNoteMarker(id, elements[0]);
        } else if (!hasNotes && noteMarker) {
            noteMarker.parentNode.removeChild(noteMarker);
            delete noteMarkerIdMap[id];
        }
    }

    function deleteAnnotation(id) {
        var elements = getElementsForAnnotation(id);
        for (var _i9 = 0; _i9 < elements.length; _i9++) {
            var _element4 = elements[_i9];
            var parent = _element4.parentNode;
            while (_element4.childNodes.length > 0) {
                parent.insertBefore(_element4.childNodes[0], _element4);
            }
            parent.removeChild(_element4);
            trackedElements.splice(trackedElements.indexOf(_element4), 1);
        }
        var noteMarker = noteMarkerIdMap[id];
        if (noteMarker) {
            noteMarker.parentNode.removeChild(noteMarker);
            delete noteMarkerIdMap[id];
        }
    }

    function getSelectionBounds(selRange) {
        var unfilteredRects = selRange.getClientRects();
        var rects = filterRectsOnScreen(unfilteredRects);
        if (rects.length == 0) {
            rects = unfilteredRects;
        }

        var left = Math.min(rects[0].left, rects[rects.length - 1].right);
        var top = rects[0].top;
        var right = Math.max(rects[0].left, rects[rects.length - 1].right);
        var bottom = rects[rects.length - 1].bottom;

        return { 'left': left, 'top': top, 'right': right, 'bottom': bottom };
    }

    function requestUpdatedBounds(requestId, annotationId) {
        var bounds = void 0;
        if (annotationId != -1) {
            bounds = getAnnotationBounds(annotationId);
        } else {
            bounds = getSelectionBounds(lastSelectionRange);
        }
        liNativeAnnotations.onBoundsUpdated(requestId, bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    function forceRepaint() {
        document.body.style.transform = 'translateZ(0)';
        document.body.offsetHeight;
        document.body.style.transform = '';
    }

    function onClick() {
        if (!isRangeEmpty(lastSelectionRange)) {
            // Check the selection now to avoid a blink when an annotation is created because of the
            // time between the selection going away and the tracker hitting.
            checkSelection();
        }
    }

    function createNoteMarker(id, parent) {
        var element = document.createElement('div');
        element.style.position = 'absolute';
        element.style.padding = '0px !important';
        element.style.margin = '0px !important';
        element.style.width = NOTE_MARKER_WIDTH + 'px';
        element.style.height = NOTE_MARKER_HEIGHT + 'px';
        if (noteMarkersHidden) {
            element.style.opacity = 0;
        } else {
            element.style.opacity = NOTE_MARKER_ALPHA;
        }
        element.style.background = 'url(file:///android_res/drawable/ic_note_marker.png)';
        element.style.backgroundSize = '100% 100%';
        element.style.left = -(NOTE_MARKER_WIDTH / 2) + 'px';
        element.style.top = -(NOTE_MARKER_HEIGHT / 2) + 'px';
        element.style.pointerEvents = 'none';
        element.dataset.annotationId = id;
        parent.style.position = 'relative';
        parent.appendChild(element);
        return element;
    }

    function hideNoteMarkers() {
        if (noteMarkersHidden) {
            return;
        }
        noteMarkersHidden = true;

        for (var id in noteMarkerIdMap) {
            animateMarkerAlpha(noteMarkerIdMap[id], NOTE_MARKER_ALPHA, 0, NOTE_MARKER_ANIMATION_DURATION);
        }
    }

    function showNoteMarkers() {
        if (!noteMarkersHidden) {
            return;
        }
        noteMarkersHidden = false;

        for (var id in noteMarkerIdMap) {
            animateMarkerAlpha(noteMarkerIdMap[id], 0, NOTE_MARKER_ALPHA, NOTE_MARKER_ANIMATION_DURATION);
        }
    }

    function animateMarkerAlpha(marker, sourceAlpha, targetAlpha, duration, then) {
        var now = Date.now ? Date.now : function () {
            return +new Date();
        };
        var start = now();
        var diff = targetAlpha - sourceAlpha;
        var frame = function frame() {
            var progress = (now() - start) / duration;
            if (progress > 1) {
                progress = 1;
            }
            marker.style.opacity = sourceAlpha + progress * diff;
            if (progress < 1) {
                window.requestAnimationFrame(frame);
            } else if (then) {
                then();
            }
        };
        frame();
    }

    function onThemeApplied(theme) {
        useLightDefaultSelectionColor = theme ? theme.bgIsDark : false;
        setSelectionColor(selectionColor);
    }

    document.addEventListener('DOMContentLoaded', function () {
        init();
    });

    return {
        // For JS:
        getElementsForAnnotation: getElementsForAnnotation,
        onThemeApplied: onThemeApplied,

        // For Java:
        setSelectionColor: setSelectionColor,
        updateAnnotation: updateAnnotation,
        restoreAnnotation: restoreAnnotation,
        deleteAnnotation: deleteAnnotation,
        clearSelection: function clearSelection() {
            window.getSelection().removeAllRanges();
        },
        requestUpdatedBounds: requestUpdatedBounds,
        hideNoteMarkers: hideNoteMarkers,
        showNoteMarkers: showNoteMarkers,
        onClick: onClick
    };
}();
//# sourceMappingURL=../../../../maps/annotations.js.map
