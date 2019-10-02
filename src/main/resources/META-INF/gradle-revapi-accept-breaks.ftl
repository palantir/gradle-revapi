<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#-- @ftlvariable name="analysis" type="org.revapi.AnalysisContext" -->
[
<#list reports as report>
<#list report.differences as diff>
 {
  "code": ${diff.code},
  "old": ${report.oldElement!"null"},
  "new": ${report.newElement!"null"},
  "justification": "<needs justification>",
 },
</#list>
</#list>
]
