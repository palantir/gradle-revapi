<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#-- @ftlvariable name="analysis" type="org.revapi.AnalysisContext" -->
[
<#list reports as report>
<#list report.differences as diff>
    {
        "code": "${diff.code}",
        "oldElement": ${("\"" + report.oldElement.toString()?json_string + "\"")!"null"},
        "newElement": ${("\"" + report.newElement.toString()?json_string + "\"")!"null"},
        "description": "${diff.description?json_string}",
        "oldArchiveName": "${report.oldElement.archive.name?json_string}",
        "newArchiveName": "${report.newElement.archive.name?json_string}",
        "statuses": {
<#list diff.classification as compatibilityType, differenceSeverity>
            "${compatibilityType}": "${differenceSeverity}",
</#list>
        }
    },
</#list>
</#list>
]
