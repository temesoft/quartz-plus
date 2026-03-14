'use strict';

var __currentPage = "";
var __liveUpdateMinutes = $.cookie('__liveUpdateMinutes');
var __autoRefreshToggleCookie = $.cookie('__autoRefreshToggle');
var __autoRefreshDelayMs = 10000;
var __servicePrefix = "org/quartzplus/api";

// This call retrieves the context path for Scheduler REST API
$.get(__servicePrefix, function (data) {
    __servicePrefix = data;
}).fail(function (data) {
    processError(data, toaster, $timeout);
});


/* App Module */
var app = angular.module('app', ['ngRoute', 'toaster', 'ngAnimate']).
    config(['$routeProvider', '$locationProvider',
        function ($routeProvider, $locationProvider) {
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
    filter('shorten', function ($sce) {
        return function (input, size) {
            if (input !== undefined && input !== null) {
                if (input.length > size) {
                    return $sce.trustAsHtml("<span title='" + input + "'>" + input.substring(0, size).trim() + "...</span>");
                } else {
                    return $sce.trustAsHtml(input);
                }
            } else {
                return $sce.trustAsHtml("");
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

    if (__autoRefreshToggleCookie != undefined && __autoRefreshToggleCookie != null) {
        $("#autoRefreshToggleInput").bootstrapToggle('off');
        $(".refresh").css("display", "inline-block");
    } else {
        $("#autoRefreshToggleInput").bootstrapToggle('on');
        $(".refresh").css("display", "none");

    }

    $("#autoRefreshToggleInput").unbind("change").bind("change", function (e) {
        if ($("#autoRefreshToggleInput").prop('checked')) {
            $.removeCookie('__autoRefreshToggle');
        } else {
            $.cookie('__autoRefreshToggle', "true");
        }
    });
});
