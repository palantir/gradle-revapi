<#-- @ftlvariable name="result" type="com.palantir.gradle.revapi.RevapiResult" -->
old: ${result.oldElement()!"<none>"}
new: ${result.newElement()!"<none>"}

<#list result.classification()?keys as compat>${compat}: ${result.classification()?api.get(compat)}<#sep>, </#list>

From old archive: ${(result.oldArchiveName())!"<none>"}
From new archive: ${(result.newArchiveName())!"<none>"}

If this is an acceptable break that will not harm your users, you can ignore it in future runs like so for:

  * Just this break:
      ./gradlew {{acceptBreakTask}} --justification "{{explainWhy}}" \
        --code "${result.code()}"<#if result.oldElement()??> \
        --old "${result.oldElement()}"</#if><#if result.newElement()??> \
        --new "${result.newElement()}"</#if>
  * All breaks in this project:
      ./gradlew {{acceptAllBreaksProjectTask}} --justification "{{explainWhy}}"
  * All breaks in all projects:
      ./gradlew {{acceptAllBreaksEverywhereTask}} --justification "{{explainWhy}}"
