<#-- @ftlvariable name="report" type="org.revapi.Report" -->
<#-- @ftlvariable name="diff" type="org.revapi.Difference" -->
old: ${report.oldElement!"<none>"}
new: ${report.newElement!"<none>"}

<#list diff.classification?keys as compat>${compat}: ${diff.classification?api.get(compat)}<#sep>, </#list>

From old archive: ${(report.oldElement.archive.name)!"<none>"}
From new archive: ${(report.newElement.archive.name)!"<none>"}
