"use strict";

var HeliumJs = function () {

    var LAYOUT_REFLOW = 1;
    var LAYOUT_FIXED = 2;

    var FLOW_PAGED = 1;
    var FLOW_SCROLLED = 2;

    var bookContainer = void 0;
    var pageContainer = void 0;
    var endMarginStub = void 0;
    var pageWidth = void 0;
    var pageHeight = void 0;
    var pageHorizontalMargin = void 0;
    var pageVerticalMargin = void 0;
    var pageCount = void 0;
    var pagedHorizontally = void 0;
    var pageProperties = {};
    var marginPercent = 0;
    var lineSpacing = void 0;
    var textAlign = void 0;
    var showcasedImageElement = void 0;
    var currentFont = void 0;
    var fontSetupInitialized = void 0;
    var initialFlowWasDone = void 0;

    var typefacesStyleElement = void 0;
    var fontStyleElement = void 0;
    var styleElement = void 0;

    // List of fonts we have added CSS for.
    var fontsInjected = [];

    // List of fonts that have been loaded.
    var fontsLoaded = [];

    var specificityClassName = '';
    var specificitySelector = '';

    if (!window.requestAnimationFrame) {
        window.requestAnimationFrame = function (cb) {
            setTimeout(cb, 16);
        };
    }

    function init() {
        // Build a class list to apply to the body tag that we can use in selectors to force our
        // rules to have a higher specificity than the books stylesheet.
        for (var i = 0; i < 16; i++) {
            var className = '__Helium_X' + i;
            specificityClassName += ' ' + className;
            specificitySelector += '.' + className;
        }
        document.body.className = document.body.className + specificityClassName;

        // Since we're using CSS3 to split pages, we have to emulate page padding. For all
        // pages except the last, we can simply use the 'column-gap' property. For the last
        // page we'll create an absolutely positioned div that floats at the right-most pixel
        // of the book including padding, forcing the document width to expand to include
        // the padding. (You would think the html/body tag would expand due to CSS3 column
        // splitting, and therefore the margin/padding would automatically be applied to
        // the last page, however it doesn't.)
        endMarginStub = document.createElement('div');
        endMarginStub.style.position = 'absolute';
        endMarginStub.style.top = '0px';
        endMarginStub.style.height = '1px';
        endMarginStub.style.width = '1px';
        resetStyles([endMarginStub]);
        document.body.appendChild(endMarginStub);

        typefacesStyleElement = document.createElement('style');
        typefacesStyleElement.setAttribute('type', 'text/css');
        document.head.appendChild(typefacesStyleElement);

        fontStyleElement = document.createElement('style');
        fontStyleElement.setAttribute('type', 'text/css');
        document.head.appendChild(fontStyleElement);

        styleElement = document.createElement('style');
        styleElement.setAttribute('type', 'text/css');
        document.head.appendChild(styleElement);

        var _arr = document.getElementsByTagName('a');

        for (var _i = 0; _i < _arr.length; _i++) {
            var element = _arr[_i];
            if (!element.getAttribute('href')) continue;
            element.addEventListener('click', function (e) {
                onAnchorClick(this, e);
            });
        }

        var _arr2 = document.getElementsByTagName('img');

        for (var _i2 = 0; _i2 < _arr2.length; _i2++) {
            var _element = _arr2[_i2];
            _element.addEventListener('click', function (e) {
                onImageClick(this, e);
            });
        }

        document.addEventListener('click', function () {
            HeliumApp.onTouchUp();
        });

        parseSwitchElements();
    }

    function postInit() {
        var metaElements = document.getElementsByTagName('meta');
        var viewportElement = null;
        for (var _i3 = 0; _i3 < metaElements.length; _i3++) {
            var element = metaElements[_i3];
            if (element.getAttribute('name') == 'viewport') {
                viewportElement = element;
                break;
            }
        }
        var hasViewport = !!viewportElement;
        if (hasViewport && pageProperties.layoutStyle == LAYOUT_FIXED) {
            var viewportOptions = parseViewportString(viewportElement.getAttribute('content'));
            var width = parseInt(viewportOptions['width']);
            var height = 0;
            if (viewportOptions['height']) {
                height = parseInt(viewportOptions['height']);
            }

            var html = document.getElementsByTagName('html')[0];
            var htmlBounds = html.getBoundingClientRect();
            height = Math.max(height, htmlBounds.bottom - htmlBounds.top);

            viewportOptions['width'] = width;
            viewportOptions['height'] = height;

            // Must set a minimum-scale of 0 to allow scale downs to half-size, such as in our
            // tests.
            viewportOptions['minimum-scale'] = 0;

            // Send the viewport up.
            HeliumApp.setFixedViewport(width, height);

            viewportElement.setAttribute('content', buildViewportString(viewportOptions));
        } else {
            if (hasViewport) {
                viewportElement.parentNode.removeChild(viewportElement);
            }

            // Add a device-width viewport if the page isn't fixed viewport.
            var elem = document.createElement('meta');
            elem.setAttribute('name', 'viewport');
            elem.setAttribute('content', 'width=device-width, user-scalable=no');
            document.head.appendChild(elem);
        }

        HeliumApp.notifySize(window.innerWidth, window.innerHeight);

        setupFlow();

        window.onresize = function () {
            HeliumApp.notifySize(window.innerWidth, window.innerHeight);
            setupFlow();
        };

        // Notify our app the book is ready to be displayed.
        HeliumApp.onBookReady();

        // Update the window size after some time to allow layout to take place.
        setTimeout(function () {
            HeliumApp.notifySize(window.innerWidth, window.innerHeight);
        }, 300);
    }

    /**
     * Parses a viewport string and returns an object.
     */
    function parseViewportString(str) {
        var viewport = {};
        var optStrs = str.split(',');
        for (var i = 0; i < optStrs.length; ++i) {
            var optSplit = optStrs[i].split('=');
            var key = optSplit[0].trim();
            var value = optSplit[1].trim();
            viewport[key] = value;
        }
        return viewport;
    }

    /**
     * Builds a viewport string based on a map object.
     */
    function buildViewportString(viewport) {
        var str = "";
        var i = 0;
        for (var key in viewport) {
            if (i != 0) str += ',';
            str += key + '=' + viewport[key];
            i++;
        }
        return str;
    }

    function setPageProperties(props) {
        for (var key in props) {
            pageProperties[key] = props[key];
        }
    }

    function setupFlow() {
        if (pageProperties.flowStyle == FLOW_PAGED) {
            setupPaging();
        } else {
            // setupPaging() calls this already.
            uploadAnchorPositions(pageProperties.tocAnchorList);
        }

        var paperPageMap = getPaperPageMapping(pageProperties.paperPageToAnchorMap);
        HeliumApp.setPaperPageMap(JSON.stringify(paperPageMap));

        initialFlowWasDone = true;
    }

    function reflowIfNecessary() {
        if (initialFlowWasDone) {
            setupFlow();
        }
    }

    function setupPaging() {
        if (pageProperties.layoutStyle != LAYOUT_REFLOW || pageProperties.flowStyle != FLOW_PAGED) {
            return;
        }

        if (!pageContainer) {
            bookContainer = document.createElement('div');

            // Prevent margin collapse to prevent issue where margin shifts the
            // columns. Use a wrapper which at least works around crbug.com/1014381.
            //
            // On older WebViews (Android 4.1) this property seems to be bugged and also discards the
            // margin-top from the first item.
            var collapseWrapper = document.createElement('div');
            collapseWrapper.style.webkitMarginCollapse = 'separate';

            var bodyNodes = Array.prototype.slice.call(document.body.childNodes);
            for (var _i4 = 0; _i4 < bodyNodes.length; _i4++) {
                var node = bodyNodes[_i4];
                collapseWrapper.appendChild(node);
            }

            bookContainer.appendChild(collapseWrapper);

            pageContainer = document.createElement('div');
            pageContainer.appendChild(bookContainer);

            document.body.appendChild(pageContainer);
            resetStyles([pageContainer, bookContainer]);
        }

        // Android 4.1 WebView seems to break with fractional columns.
        pageHorizontalMargin = Math.round(window.innerWidth * marginPercent) * 2;
        pageVerticalMargin = pageHorizontalMargin;

        pageWidth = window.innerWidth - pageHorizontalMargin;
        pageHeight = window.innerHeight - pageVerticalMargin;

        var bodyComputed = window.getComputedStyle(document.body);
        var htmlComputed = window.getComputedStyle(document.getElementsByTagName('html')[0]);
        pagedHorizontally = bodyComputed.writingMode.indexOf('vertical') == -1 && htmlComputed.writingMode.indexOf('vertical') == -1;

        bookContainer.style.width = pageWidth + 'px';
        bookContainer.style.height = pageHeight + 'px';
        bookContainer.style.webkitColumnWidth = (pagedHorizontally ? pageWidth : pageHeight) + 'px';
        bookContainer.style.webkitColumnGap = (pagedHorizontally ? pageHorizontalMargin : pageVerticalMargin) + 'px';

        endMarginStub.style.left = '0px';
        endMarginStub.style.top = '0px';

        if (pagedHorizontally) {
            pageCount = Math.ceil(document.body.scrollWidth / (pageWidth + pageHorizontalMargin));
            endMarginStub.style.left = pageCount * (pageWidth + pageHorizontalMargin) - 1 + 'px';
        } else {
            pageCount = Math.ceil(document.body.scrollHeight / (pageHeight + pageVerticalMargin));
            endMarginStub.style.top = pageCount * (pageHeight + pageVerticalMargin) - 1 + 'px';
        }

        // Work around a bug in older WebView versions where an absolutely positioned element at the
        // right edge of the document creates extra space equal to the margin of the page.
        if (pageProperties.apiLevel < 21) {
            setTimeout(function () {
                endMarginStub.parentNode.removeChild(endMarginStub);
                pageContainer.appendChild(endMarginStub);
            }, 100);
        }

        uploadAnchorPositions(pageProperties.tocAnchorList);

        HeliumApp.onPagingSetup(pageHorizontalMargin / window.innerWidth, pageHorizontalMargin / window.innerHeight, pageCount, pagedHorizontally);
    }

    function resetStyles(elements) {
        for (var index = 0; index < elements.length; ++index) {
            var element = elements[index];
            element.style.margin = '0px';
            element.style.padding = '0px';
            element.style.border = 'none';
        }
    }

    function parseSwitchElements() {
        var switches = document.getElementsByTagName('switch');
        for (var _i5 = 0; _i5 < switches.length; _i5++) {
            var element = switches[_i5];
            var cases = element.getElementsByTagName('case');
            for (var _i6 = 0; _i6 < cases.length; _i6++) {
                var e = cases[_i6];
                e.parentNode.removeChild(e);
            }
        }
    }

    function onAnchorClick(elem, e) {
        if (elem.href.indexOf('javascript:') == 0) {
            e.stopPropagation();
            return;
        }

        e.preventDefault();
        e.stopPropagation();

        var epubType = elem.getAttribute('epub:type') || elem.getAttribute('epubu0003atype');
        if (epubType == 'noteref') {
            e.preventDefault();
            e.stopPropagation();

            fetchFootnote(elem.getAttribute('href'), function (html) {
                var rects = elem.getClientRects();
                var rect = void 0;
                for (var index in rects) {
                    var r = rects[index];
                    if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) {
                        rect = r;
                        break;
                    }
                }

                // It's possible no rects where found that contained the click,
                // for example during unit tests where the click does not have
                // a position.
                rect = rect || rects[0];

                var x = (rect.left + rect.right) / 2;
                var y = (rect.top + rect.bottom) / 2;

                HeliumApp.openFootNote(elem.innerText.trim(), html, x, y);
            });
        } else {
            HeliumApp.openLink(elem.href);
        }
    }

    function fetchFootnote(href, callback) {
        if (href[0] == '#') {
            var footnoteHtml = getFootnoteHtmlFromElement(document.getElementById(href.substring(1)));
            callback(getFootnoteHeaders(document) + footnoteHtml);
            return;
        }

        var anchorIndex = href.indexOf('#');
        var anchor = href.substring(anchorIndex);
        var file = href.substring(0, anchorIndex);

        // Send an AJAX request for the target page.
        var request = new XMLHttpRequest();
        request.open('GET', file, true);
        request.responseType = 'document';
        request.onload = function () {
            if (request.status != 200) {
                console.error('Failed to load footnote at "' + href + '": ' + request.statusText);
                return;
            }
            var data = request.responseXML;
            callback(getFootnoteHeaders(data) + getFootnoteHtmlFromElement(data.getElementById(anchor.substring(1))));
        };
        request.send();
    }

    function getFootnoteHtmlFromElement(elem) {
        var currentElem = elem;
        var newTree = void 0;

        while (currentElem.tagName && currentElem.tagName.toLowerCase() !== 'body' && currentElem != bookContainer) {
            if (newTree) {
                var oldParent = newTree;
                newTree = currentElem.cloneNode(false);
                newTree.className += ' __Helium_NoEffects';
                newTree.appendChild(oldParent);
            } else {
                newTree = currentElem.cloneNode(true);
            }

            currentElem = currentElem.parentNode;
        }

        var container = document.createElement('div');
        container.className = '__Helium_Footnote_Content ' + specificityClassName;
        container.appendChild(newTree);
        return container.outerHTML;
    }

    function getFootnoteHeaders(container) {
        var footNoteBootstrap = "";
        footNoteBootstrap += "<link rel='stylesheet' type='text/css' href='file:///android_asset/css/default.css' />";

        var elements = container.querySelectorAll('link, style');
        var styles = document.createElement('div');
        for (var _i7 = 0; _i7 < elements.length; _i7++) {
            var elem = elements[_i7];
            if (elem.dataset.excludeFromFootnote) {
                continue;
            }
            styles.appendChild(elem.cloneNode(true));
        }
        footNoteBootstrap += styles.innerHTML;

        footNoteBootstrap += "<style type='text/css'>" + HeliumThemes.getCss() + "</style>";
        footNoteBootstrap += "<link rel='stylesheet' type='text/css' href='file:///android_asset/css/footnote.css' />";
        return footNoteBootstrap;
    }

    function jumpToElement(element) {
        if (pageProperties.flowStyle == FLOW_PAGED) {
            HeliumApp.setPage(getPageForElement(element));
        } else if (pageProperties.flowStyle == FLOW_SCROLLED) {
            var bounds = element.getBoundingClientRect();
            scrollTo(0, bounds.top + window.scrollY);
        }
    }

    function jumpToAnchor(href) {
        var element = document.getElementById(href.substring(1));
        if (!element) {
            console.error('No anchor at ' + href);
            return;
        }
        jumpToElement(document.getElementById(href.substring(1)));
    }

    function jumpToAnnotation(id) {
        var elements = HeliumAnnotations.getElementsForAnnotation(id);
        if (elements.length) jumpToElement(elements[0]);
    }

    function jumpToSearchResult(start, end) {
        // Discard any previous results.
        var prevElems = document.getElementsByClassName('__Helium_SearchResult');
        for (var _i8 = 0; _i8 < prevElems.length; _i8++) {
            var elem = prevElems[_i8];
            elem.className = '';
        }

        var applier = rangy.createClassApplier('__Helium_SearchResult');
        applier.useExistingElements = false;

        var highlighter = rangy.createHighlighter();
        highlighter.addClassApplier(applier, { normalize: false });
        highlighter.deserialize('type:textContent|' + start + '$' + end + '$0$__Helium_SearchResult$');

        var elems = document.getElementsByClassName('__Helium_SearchResult');
        if (elems.length) {
            (function () {
                var rgb = '240, 55, 55';
                for (var _i9 = 0; _i9 < elems.length; _i9++) {
                    var elem = elems[_i9];
                    elem.style.setProperty('background-color', 'rgba(' + rgb + ', 0.5)', 'important');
                }
                jumpToElement(elems[0]);

                var hide = function hide() {
                    var start = +new Date();
                    var animate = function animate() {
                        var now = +new Date();
                        var progress = Math.min((now - start) / 500, 1);
                        var alpha = (1 - progress) * 0.5;
                        for (var _i10 = 0; _i10 < elems.length; _i10++) {
                            var elem = elems[_i10];
                            elem.style.setProperty('background-color', 'rgba(' + rgb + ', ' + alpha + ')', 'important');
                        }
                        if (progress < 1) {
                            requestAnimationFrame(animate);
                        }
                    };
                    animate();
                };
                setTimeout(hide, 1000);
            })();
        }
    }

    function getPageForElement(element) {
        var bounds = element.getBoundingClientRect();
        if (pagedHorizontally) {
            return Math.floor((bounds.left + window.scrollX) / (pageWidth + pageHorizontalMargin));
        }
        return Math.floor((bounds.top + window.scrollY) / (pageHeight + pageVerticalMargin));
    }

    function onImageClick(element, e) {
        var parent = element.parentNode;
        while (parent) {
            if (parent.tagName && parent.tagName.toLowerCase() == 'a' && parent.getAttribute('href')) {
                return;
            }
            parent = parent.parentNode;
        }

        e.preventDefault();
        e.stopPropagation();

        var bounds = element.getBoundingClientRect();
        HeliumApp.openImage(element.src, bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top);
        showcasedImageElement = element;
    }

    function hideShowcasedImage() {
        if (showcasedImageElement) {
            showcasedImageElement.style.visibility = 'hidden';
        }
    }

    function restoreShowcasedImage() {
        if (showcasedImageElement) {
            showcasedImageElement.style.visibility = 'visible';
            showcasedImageElement = null;
        }
    }

    function uploadAnchorPositions(chapterAnchors) {
        if (!chapterAnchors.length) {
            HeliumApp.setAnchorPositions(null);
            return;
        }

        // Find the positions of the anchors.
        var anchorPositions = [];
        for (var index in chapterAnchors) {
            var anchor = chapterAnchors[index];
            var element = document.getElementById(anchor.substring(1));
            if (!element) {
                continue;
            }
            if (pageProperties.flowStyle == FLOW_PAGED) {
                anchorPositions.push([anchor, getPageForElement(element)]);
            } else {
                var bounds = element.getBoundingClientRect();
                var pos = bounds.top + window.scrollY;
                anchorPositions.push([anchor, pos]);
            }
        }

        HeliumApp.setAnchorPositions(JSON.stringify(anchorPositions));
    }

    function getPaperPageMapping(anchors) {
        var map = {};
        for (var page in anchors) {
            var anchor = anchors[page];
            var element = document.getElementById(anchor.substring(1));
            if (!element) {
                continue;
            }
            switch (pageProperties.flowStyle) {
                case FLOW_PAGED:
                    map[page] = getPageForElement(element);
                    break;
                case FLOW_SCROLLED:
                    map[page] = element.offsetTop;
                    break;
            }
        }
        return map;
    }

    function setMargin(margin) {
        marginPercent = margin / 100.0;
        document.body.style.margin = margin + '%';
        reflowIfNecessary();
    }

    function setLineSpacing(spacing) {
        lineSpacing = spacing;
        updateStyleElement();
        reflowIfNecessary();
    }

    function setTextAlign(align) {
        textAlign = align;
        updateStyleElement();
        reflowIfNecessary();
    }

    function updateStyleElement() {
        var style = 'line-height: ' + lineSpacing + " !important;";
        switch (textAlign) {
            case 1:
                // TEXT_ALIGN_START
                style += 'text-align: initial !important;';
                break;
            case 2:
                // TEXT_ALIGN_JUSTIFY
                style += 'text-align: justify !important;';
                break;
        }
        styleElement.innerText = specificitySelector + ' * { ' + style + ' }';
    }

    function fontFaceDefinition(name, style, weight, filename) {
        return "@font-face { font-family: '" + name + "';" + "font-style: " + style + "; font-weight: " + weight + ";" + "src: url(file:///android_asset/fonts/" + filename + "); }";
    }

    function addCssDefinitionForTypeface(font) {
        var fontDefinition = fontFaceDefinition(font.name, 'normal', 400, font.regular) + (font.bold ? fontFaceDefinition(font.name, 'normal', 700, font.bold) : "") + (font.italic ? fontFaceDefinition(font.name, 'italic', 400, font.italic) : "") + (font.italic ? fontFaceDefinition(font.name, 'italic', 700, font.boldItalic) : "");
        typefacesStyleElement.innerText = typefacesStyleElement.innerText + fontDefinition;
        fontsInjected.push(font.name);
    }

    function setFont(font) {
        currentFont = font;
        if (!font) {
            fontStyleElement.innerText = "";
            if (initialFlowWasDone) {
                // If the original fonts haven't been used in this page load, the layout may not be
                // ready yet, so use a small delay.
                setTimeout(function () {
                    setupFlow();
                }, 60);
            }
            return;
        }

        fontStyleElement.innerText = specificitySelector + " * { font-family: " + font.name + " !important; }";

        if (fontSetupInitialized) {
            var onFontLoadDone = function onFontLoadDone() {
                fontsLoaded.push(font.name);
                if (initialFlowWasDone) {
                    setupFlow();
                }
            };

            if (fontsLoaded.indexOf(font.name) > -1) {
                onFontLoadDone();
            } else {
                WebFont.load({
                    custom: {
                        families: [font.name]
                    },
                    classes: false,
                    active: onFontLoadDone,
                    inactive: function inactive() {
                        console.log('Failed to load font: ' + font.name);
                    },
                    timeout: 1000
                });
            }
        }

        if (fontsInjected.indexOf(font.name) == -1) {
            addCssDefinitionForTypeface(font);
        }
    }

    function isCssRulesBlocked(sheet) {
        try {
            if (sheet.cssRules == null) {
                return true;
            }
        } catch (err) {
            return true;
        }
        return false;
    }

    function getFontListFromStyleSheet(sheet) {
        if (isCssRulesBlocked(sheet)) {
            return { fonts: [], useWorkaround: true };
        }
        var fonts = [];
        var useWorkaround = false;
        var _arr3 = sheet.cssRules;
        for (var _i11 = 0; _i11 < _arr3.length; _i11++) {
            var rule = _arr3[_i11];
            if (rule.styleSheet) {
                var res = getFontListFromStyleSheet(rule.styleSheet);
                fonts.push.apply(fonts, res.fonts);
                useWorkaround = useWorkaround || res.useWorkaround;
                continue;
            }
            if (rule.type == CSSRule.FONT_FACE_RULE) {
                var face = rule.style.fontFamily;
                if (fonts.indexOf(face) == -1) {
                    fonts.push(face);
                }
            }
        }
        return { fonts: fonts, useWorkaround: useWorkaround };
    }

    function getFontList() {
        var fonts = [];
        var fontAddedMap = {};
        var useWorkaround = void 0;
        var _arr4 = document.styleSheets;
        for (var _i12 = 0; _i12 < _arr4.length; _i12++) {
            var sheet = _arr4[_i12];
            var res = getFontListFromStyleSheet(sheet);
            useWorkaround = useWorkaround || res.useWorkaround;
            var _arr5 = res.fonts;
            for (var _i15 = 0; _i15 < _arr5.length; _i15++) {
                var font = _arr5[_i15];
                fontAddedMap[font.toLowerCase()] = true;
            }
            fonts.push.apply(fonts, res.fonts);
        }

        if (useWorkaround) {
            var bodyNodes = Array.prototype.slice.call(document.body.childNodes);
            for (var _i13 = 0; _i13 < bodyNodes.length; _i13++) {
                var node = bodyNodes[_i13];
                if (node.nodeType != Node.ELEMENT_NODE) {
                    continue;
                }
                var style = window.getComputedStyle(node);
                var families = style['font-family'].split(',');
                for (var _i14 = 0; _i14 < families.length; _i14++) {
                    var family = families[_i14];
                    var lower = family.toLowerCase();
                    if (fontAddedMap[lower] === true) {
                        continue;
                    }
                    fontAddedMap[lower] = true;
                }
                fonts.push.apply(fonts, families);
            }
        }

        return fonts;
    }

    document.addEventListener('DOMContentLoaded', init);
    window.addEventListener('load', function () {
        if (pageProperties.layoutStyle == LAYOUT_FIXED) {
            postInit();
            return;
        }

        var families = getFontList();
        if (currentFont) {
            if (families.indexOf(currentFont.name) == -1) {
                families.push(currentFont.name);
            }
        }
        fontSetupInitialized = true;

        WebFont.load({
            custom: {
                families: families
            },
            classes: false,
            active: function active() {
                fontsLoaded.push.apply(fontsLoaded, families);
                postInit();
            },
            inactive: postInit,
            timeout: 1000
        });
    });

    return {
        jumpToAnchor: jumpToAnchor,
        jumpToAnnotation: jumpToAnnotation,
        jumpToSearchResult: jumpToSearchResult,
        hideShowcasedImage: hideShowcasedImage,
        restoreShowcasedImage: restoreShowcasedImage,
        reflowIfNecessary: reflowIfNecessary,
        setPageProperties: setPageProperties,
        setMargin: setMargin,
        setFont: setFont,
        setLineSpacing: setLineSpacing,
        setTextAlign: setTextAlign
    };
}();