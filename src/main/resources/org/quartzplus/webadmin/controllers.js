"use strict";
var scopeDebug = null;

function ConfigurationController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Configuration");
    $.get(__servicePrefix + "/config",function (data) {
        $scope.properties = data;
        $scope.$apply();
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
}

function JobsController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Jobs");
    $.get(__servicePrefix + "/jobs" + createQueryString($routeParams),function (data) {
        $scope.jobs = data;
        $scope.$apply();
        $scope.executeJobNow = function () {
            var group = $('#executeJobModal .groupName').html();
            var job = $('#executeJobModal .jobName').html();
            var trigger = $('#executeJobModal .triggerName').html();
            var data = $('#executeJobModal textarea').val();
            $(".executeTriggerBtn").button('loading');
            $.ajax({ url: __servicePrefix + "/trigger/execute?groupName=" + group + "&triggerName=" + trigger + "&jobName=" + job,
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }, data: data, type: 'POST'})
                .done(function () {
                    $(".executeTriggerBtn").button('reset');
                    $('#executeJobModal').modal("hide");
                    window.location = ("#/triggers");
                    toaster.pop('success', '', "Job \"" + group + "." + job + "\" was sent for immediate execution");
                })
                .fail(function (data) {
                    $(".executeTriggerBtn").button('reset');
                    $('#executeJobModal').modal("hide");
                    processError(data, toaster, $timeout);
                });
        };
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
}

function TriggersController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Triggers");
    $.get(__servicePrefix + "/triggers" + createQueryString($routeParams),function (data) {
        $scope.triggers = data;
        $scope.$apply();
        reloadController($scope, "Triggers", function () {
            TriggersController($scope, $routeParams, toaster, $timeout);
        });
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
}

function LogsController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Logs");
    $scope.page = 0;
    $scope.pageSize = 10;
    $scope.paginationRange = function(min, max, currentPage) {
        return paginationRange(min, max, currentPage);
    };

    $scope.listLogs = function(pageSize, page) {
        if (pageSize != undefined) {
            $scope.pageSize = pageSize;
        }
        if (page != undefined) {
            $scope.page = page;
        }
        $routeParams.pageSize = $scope.pageSize;
        $routeParams.currentPage = $scope.page;
        $.get(
            __servicePrefix + "/log" + createQueryString($routeParams),
            function (data) {
                $scope.logs = data;
                $scope.$apply();
                reloadController($scope, "Logs", function () {
                    // LogsController($scope, $routeParams, toaster, $timeout);
                    $scope.listLogs($scope.pageSize, $scope.page); // TODO: change this?
                });
            }).fail(function (data) {
                processError(data, toaster, $timeout);
            });
    };

    $.get(__servicePrefix + "/log" + createQueryString($routeParams),function (data) {
        $scope.logs = data;
        $scope.$apply();
        reloadController($scope, "Logs", function () {
            LogsController($scope, $routeParams, toaster, $timeout);
        });
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
}

function LogDetailsController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Logs");
    $.get(__servicePrefix + "/log" + createQueryString($routeParams),function (data) {
        $scope.log = data;
        $scope.$apply();
        $("span[title]").tooltip();
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
}

function TriggerDetailsController($scope, $routeParams, toaster, $timeout, $location) {
    setMenuItemActive($scope, "Triggers");
    if ($routeParams.groupName !== undefined && $routeParams.triggerName !== undefined) {
        $.get(__servicePrefix + "/trigger?groupName=" + $routeParams.groupName + "&triggerName=" + $routeParams.triggerName,function (data) {
            $scope.triggerDetails = data;

            $scope.pauseTrigger = function (group, trigger) {
                $(".pauseTriggerBtn").button('loading');
                $.post(__servicePrefix + "/trigger/pause?groupName=" + group + "&triggerName=" + trigger,function (data) {
                    TriggerDetailsController($scope, $routeParams, toaster, $timeout, $location);
                    toaster.pop('success', '', "Trigger \"" + group + "." + $routeParams.triggerName + "\" is paused");
                }).fail(function (data) {
                        processError(data, toaster, $timeout);
                    });
            };
            $scope.resumeTrigger = function (group, trigger) {
                $(".resumeTriggerBtn").button('loading');
                $.post(__servicePrefix + "/trigger/resume?groupName=" + group + "&triggerName=" + trigger,function (data) {
                    TriggerDetailsController($scope, $routeParams, toaster, $timeout, $location);
                    toaster.pop('success', '', "Trigger \"" + group + "." + $routeParams.triggerName + "\" is resumed");
//                window.location = ("#/trigger/" + group + "/" + trigger + "?t=" + new Date().getTime());
                }).fail(function (data) {
                        processError(data, toaster, $timeout);
                    });
            };
            $scope.deleteTrigger = function (group, trigger) {
                if (confirm("Are you sure?\n\nDelete trigger: " + group + "." + trigger)) {
                    $(".deleteTriggerBtn").button('loading');
                    $.ajax({ url: __servicePrefix + "/trigger?groupName=" + group + "&triggerName=" + trigger, type: 'DELETE'})
                        .done(function () {
                            window.location = ("#/triggers");
                            toaster.pop('success', '', "Trigger \"" + group + "." + $routeParams.triggerName + "\" is deleted");
                        })
                        .fail(function (data) {
                            processError(data, toaster, $timeout);
                        });
                }
            };
            $scope.executeTrigger = function (group, trigger, job) {
                $('#executeJobModal').on('shown.bs.modal', function (e) {
                    $('#executeJobModal textarea').focus();
                    $('#executeJobModal textarea').select();
                })
                $('#executeJobModal').modal({show: true});
                $('#executeJobModal .groupName').html(group);
                $('#executeJobModal .jobName').html(job);
                $('#executeJobModal .triggerName').html(trigger);
            };
            $scope.$apply();
            reloadController($scope, "Triggers", function () {
                TriggerDetailsController($scope, $routeParams, toaster, $timeout, $location);
            });
        }).fail(function (data) {
            processError(data, toaster, $timeout);
        });
    }
}

function NodesController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Nodes");
    $.get(__servicePrefix + "/nodes",function (data) {
        $scope.nodes = data;
        $scope.$apply();
        $("span[title]").tooltip();

        reloadController($scope, "Nodes", function () {
            NodesController($scope, $routeParams, toaster, $timeout);
        });
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
}

function SchedulerController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Scheduler");
    $.get(__servicePrefix + "" + createQueryString($routeParams),function (data) {
        $scope.scheduler = data;
        $scope.$apply();

        if (data != undefined && data.schedulerMetaData != undefined && !data.schedulerMetaData.jobStoreClustered) {
            $(".Nodes_menu_item").remove();
        }

        reloadController($scope, "Scheduler", function () {
            SchedulerController($scope, $routeParams, toaster, $timeout);
        });
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
}

function MetricsController($scope, $routeParams, toaster, $timeout) {
    setMenuItemActive($scope, "Metrics");
    $.get(__servicePrefix + "/metrics" + createQueryString($routeParams),function (data) {
        $scope.metrics = data;
        $scope.$apply();

        reloadController($scope, "Metrics", function () {
            MetricsController($scope, $routeParams, toaster, $timeout);
        });
    }).fail(function (data) {
        processError(data, toaster, $timeout);
    });
    scopeDebug = $scope;
}

function reloadController($scope, viewId, controllerCallback) {
    if ($("#autoRefreshToggleInput").prop('checked') && __currentPage === viewId) {
        setTimeout(function () {
            if ($("#autoRefreshToggleInput").prop('checked') && __currentPage === viewId) {
                controllerCallback();
            }
        }, __autoRefreshDelayMs);
    }
}

function createQueryString($routeParams) {
    var queryString = "";
    var firstEntry = true;
    for (var obj in $routeParams) {
        if (!firstEntry) {
            queryString += "&";
        }
        queryString += (obj + "=" + $routeParams[obj]);
        firstEntry = false;
    }
    if (queryString.length > 0) {
        queryString = "?" + queryString;
    }
    return queryString;
}

function processError(data, toaster, $timeout) {
    "use strict";
    if (console !== undefined && console.log !== undefined) {
        console.log(data.responseText);
    }

    $timeout(function () {
        if (data.responseText !== undefined && data.responseText !== null) {
            var resp = JSON.parse(data.responseText);
            if (resp.message !== undefined && resp.message !== null) {
                console.log('Communication error: ' + resp.message);
                toaster.pop('error', 'Communication error', resp.message);
            } else {
                console.log('Network error: ' + data.responseText);
                toaster.pop('error', 'Network error', data.responseText);
            }
        } else {
            console.log('Problem making an api request: ' + data);
            toaster.pop('error', 'Problem making an api request', data);
        }
    }, 0);
}
