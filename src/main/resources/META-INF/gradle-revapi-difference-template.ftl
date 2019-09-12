<#-- @ftlvariable name="report" type="org.revapi.Report" -->
<#-- @ftlvariable name="diff" type="org.revapi.Difference" -->
old: ${report.oldElement!"<none>"}
new: ${report.newElement!"<none>"}

<#list diff.classification?keys as compat>${compat}: ${diff.classification?api.get(compat)}<#sep>, </#list>

From old archive: ${(report.oldElement.archive.name)!"<none>"}
From new archive: ${(report.newElement.archive.name)!"<none>"}

If this is an acceptable break that will not harm your users, you can ignore it in future runs
like so for:

  * Just this break:
      ./gradlew :projectname:revapiAcceptBreak --justification "{why this break is ok}" \
        --code "${diff.code}"<#if report.oldElement??> \
        --old "${report.oldElement}"</#if><#if report.newElement??> \
        --new "${report.newElement}"</#if>
  * All breaks in this project:
      ./gradlew :projectname:revapiAcceptAllBreaks --justification "{why this break is ok}"
  * All breaks in all projects:
      ./gradlew revapiAcceptAllBreaks --justification "{why this break is ok}"
