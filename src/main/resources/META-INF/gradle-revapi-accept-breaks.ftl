<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#-- @ftlvariable name="analysis" type="org.revapi.AnalysisContext" -->
[
<#list reports as report>
<#list report.differences as diff>
 {
  "code": "${diff.code}",
  "old": ${("\"" + report.oldElement.toString()?json_string + "\"")!"null"},
  "new": ${("\"" + report.newElement.toString()?json_string + "\"")!"null"},
  "justification": "needs justification"
 },
</#list>
</#list>
]
