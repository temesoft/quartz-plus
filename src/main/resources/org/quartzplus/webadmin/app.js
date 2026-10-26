'use strict';

var __currentPage = "";
// cookies are scoped to the page's own path, not the whole host
var __cookies = Cookies.withAttributes({path: ''});
var __liveUpdateMinutes = __cookies.get('__liveUpdateMinutes');
var __autoRefreshStorageKey = 'quartz-plus.auto-refresh';
var __autoRefreshEnabled = __storedPreference(__autoRefreshStorageKey) !== 'off';
var __autoRefreshDelayMs = 10000;
// increased by every scheduled reload; a pending reload runs only while it is still the latest
var __refreshGeneration = 0;

// earlier versions kept "refresh disabled" in a session cookie: carry it over once
if (__cookies.get('__autoRefreshToggle') !== undefined) {
    __autoRefreshEnabled = false;
    __storePreference(__autoRefreshStorageKey, 'off');
    __cookies.remove('__autoRefreshToggle');
}
var __servicePrefix = "org/quartzplus/api";
var __csrfToken = $("meta[name='_csrf']").attr("content");
var __csrfHeader = $("meta[name='_csrf_header']").attr("content");

// Sends the host's CSRF token, rendered into the page by the server, with every request that changes something
if (__csrfToken && __csrfHeader) {
    $.ajaxSetup({
        beforeSend: function (xhr, settings) {
            if (!/^(GET|HEAD|OPTIONS)$/i.test(settings.type)) {
                xhr.setRequestHeader(__csrfHeader, __csrfToken);
            }
        }
    });
}

// This call retrieves the context path for Scheduler REST API; the controllers use it, so Angular starts only after it
$.get(__servicePrefix, function (data) {
    __servicePrefix = data;
    angular.bootstrap(document, ['app']);
}).fail(function (data) {
    console.log('Unable to load the scheduler API address: ' + data.status);
});


/* App Module */
var app = angular.module('app', ['ngRoute', 'toaster', 'ngAnimate']).
    config(['$routeProvider', '$locationProvider',
        function ($routeProvider, $locationProvider) {
            $locationProvider.hashPrefix('');
            // Define routes
            $routeProvider
                .when('/configuration', { templateUrl: 'inc/configuration.html', controller: ConfigurationController})
                .when('/triggers', { templateUrl: 'inc/triggers.html', controller: TriggersController})
                .when('/trigger/:groupName/:triggerName', { templateUrl: 'inc/trigger-details.html', controller: TriggerDetailsController})
                .when('/logs', { templateUrl: 'inc/logs.html', controller: LogsController})
                .when('/log/:id', { templateUrl: 'inc/log-details.html', controller: LogDetailsController})
                .when('/nodes', { templateUrl: 'inc/nodes.html', controller: NodesController})
                .when('/scheduler', { templateUrl: 'inc/scheduler.html', controller: SchedulerController})
                .when('/metrics', { templateUrl: 'inc/metrics.html', controller: MetricsController})
                .when('/jobs', { templateUrl: 'inc/jobs.html', controller: JobsController})
                .otherwise({redirectTo: '/scheduler'});
        }]).
    filter('moment',function () {
        return function (input, momentFn /*, param1, param2, ...param n */) {
            if (input !== undefined && input !== null) {
                var args = Array.prototype.slice.call(arguments, 2),
                    momentObj = moment(input);
                return momentObj[momentFn].apply(momentObj, args);
            } else {
                return "";
            }
        };
    }).
    filter('replaceAll',function () {
        return function (input, src, trg) {
            if (input !== undefined && input !== null) {
                return input.replaceAll(src, trg);
            } else {
                return "";
            }
        };
    }).
    filter('shorten', function () {
        return function (input, size) {
            if (input !== undefined && input !== null) {
                if (input.length > size) {
                    return input.substring(0, size).trim() + "...";
                } else {
                    return input;
                }
            } else {
                return "";
            }
        };
    });


function paginationRange(min, max, currentPage) {
    let step = Math.floor((max - min) / 6);
    if (step < 1) {
        step = 1;
    }
    let input = [];
    let previousNumber = null;
    let currentPageAdded = false;
    for (let i = min; i <= max; i += step) {
        if (previousNumber != null && previousNumber < currentPage && currentPage < i) {
            input.push(currentPage);
            currentPageAdded = true;
        }
        if (currentPage === i) {
            currentPageAdded = true;
        }
        input.push(i);
        previousNumber = i;
    }
    if (!currentPageAdded) {
        input.push(currentPage);
    }
    if (input.indexOf(max) < 0) {
        input.push(max);
    }
    return input;
}

function setMenuItemActive($scope, viewId) {
    $(".nav-sidebar LI").removeClass("active");
    $(".nav-sidebar ." + viewId + "_menu_item").addClass("active");
    __currentPage = viewId;
}

function dateFromMs(ms) {
    var d = new Date();
    d.setTime(ms);
    return d;
}

String.prototype.replaceAll = function (search, replacement) {
    var target = this;
    return target.split(search).join(replacement);
};

$(document).ready(function(){
    $('#autoRefreshToggleInput').bootstrapToggle({
        on: 'refresh every 10 sec',
        off: 'refresh disabled',
        size: 'mini'
    });

    if (!__autoRefreshEnabled) {
        $("#autoRefreshToggleInput").bootstrapToggle('off');
        $(".refresh").css("display", "inline-block");
    } else {
        $("#autoRefreshToggleInput").bootstrapToggle('on');
        $(".refresh").css("display", "none");

    }

    $("#autoRefreshToggleInput").unbind("change").bind("change", function (e) {
        if ($("#autoRefreshToggleInput").prop('checked')) {
            __storePreference(__autoRefreshStorageKey, 'on');
            restartAutoRefresh();
        } else {
            __storePreference(__autoRefreshStorageKey, 'off');
            __refreshGeneration++;
        }
    });

    showThemeToggleButton();
    $("#themeToggleButton").on("click", function () {
        var theme = __currentTheme === 'dark' ? 'light' : 'dark';
        __applyTheme(theme);
        __storePreference(__themeStorageKey, theme);
        showThemeToggleButton();
    });
});

// reloads the current page, whose controller then schedules its next refresh
function restartAutoRefresh() {
    var injector = angular.element(document.body).injector();
    if (injector) {
        injector.get('$rootScope').$apply(function () {
            injector.get('$route').reload();
        });
    }
}

// the button names the theme it switches to
function showThemeToggleButton() {
    var other = __currentTheme === 'dark' ? 'light' : 'dark';
    $("#themeToggleButton .theme-toggle-label").text(other);
    $("#themeToggleButton").attr("title", "Switch to the " + other + " theme").attr("aria-label", "Switch to the " + other + " theme");
}
