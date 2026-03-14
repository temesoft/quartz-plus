# quartz-plus

Open source Java frameworkfor job scheduling and administration. It is based on two popular frameworks: [quartz job scheduler](https://quartz-scheduler.org/) and [spring](http://spring.io/).


<table class="table" border="1" >
<tr>
    <td>Using quartz-plus as a `library dependency`, allows to embed job service into your Java / Spring application or microservice</td>
    <td>Using quartz-plus as a `standalone server`, allows to inject job classes (jars) into quartz job scheduler</td>
</tr>
<tr>
    <td><a href="#/help-service">quartz-plus library</a> (configuration parameters)</td>
    <td><a href="#/help-app">quartz-plus standalone server</a></td>
</tr>
<table>

## features
We really like existing [quartz scheduler](https://quartz-scheduler.org/), but we also required some functionality that was missing in the original framework.

<table class="table" border="1" >
<thead>
<tr>
    <th>functionality description</th>
    <th class="center">quartz-plus functionality</th>
    <th class="center">original quartz functionality</th>
</tr>
</thead>
<tbody>
<tr><td>Using quartz native [configuration properties](http://www.quartz-scheduler.org/documentation/)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td class="center"><i class="glyphicon glyphicon-ok"></i></td></tr>
<tr><td>Customizable standalone and embedded job service</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>Durable pausing (remain in paused state between jvm restarts)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td class="center"><i class="glyphicon glyphicon-ok"></i></td></tr>
<tr><td>Job execution trail / log (details on each run of a job including engine (host), job name, event, timestamp, log/error stacktrace)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>Error reporting (including engine (host), job name, timestamp, message with stack trace – currently logged in db, and Job has onFailure(...) method to possibly overwrite)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>Job context (spring integration / bean wiring / SpEL processing)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>OnError retry (retry on error with specified interval between failures)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>Late time window / misfire threshold (retry time period, ability to be picked up by another cluster node)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td class="center"><i class="glyphicon glyphicon-ok"></i></td></tr>
<tr><td>Job specification can be set using annotation (<mark>@JobSpec</mark>)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>Trigger behaviour can be configured interfaces: <mark>CronTriggerable</mark>, <mark>SimpleTriggerable</mark>, <mark>OnErrorRepeatable</mark>, <mark>TimeConstrainable</mark></td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>Execute job immediately possibly with provided custom json data</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td class="center"><i class="glyphicon glyphicon-ok"></i></td></tr>
<tr><td>TimeZone (ability to schedule jobs using custom time zone)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td class="center"><i class="glyphicon glyphicon-ok"></i></td></tr>
<tr><td>Business calendar support (ability to schedule jobs taking business holidays and/or days of the week into account)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td class="center"><i class="glyphicon glyphicon-ok"></i></td></tr>
<tr><td>Job execution mertics collection (success meter, failure meter, duration timer)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>REST Scheduler interface (with swagger api docs)</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
<tr><td>UI Web Administration / REST API functionality
    <ul>
    <li>List scheduler groups</li>
    <li>List scheduler triggers</li>
    <li>List scheduler jobs</li>
    <li>Complete details view</li>
    <li>View scheduler cluster participants</li>
    <li>Display triggers/jobs execution log</li>
    <li>Timeline graph displaying execution log</li>
    <li>Create a new triggers/jobs</li>
    <li>Specify custom json data for immediate job triggering</li>
    <li>Remove triggers/jobs</li>
    <li>Pause/resume trigger/job</li>
    <li>Cluster scheduler nodes / multi-tenant visualization</li>
    </ul>
</td><td class="center"><i class="glyphicon glyphicon-ok"></i></td><td>&nbsp;</td></tr>
</tbody>
<table>
