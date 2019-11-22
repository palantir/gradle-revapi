<#-- @ftlvariable name="results" type="com.palantir.gradle.revapi.RevapiResults" -->
<#list results.results() as result>
${result.code()}<#if result.description()??>: ${result.description()}</#if>

<#include "gradle-revapi-difference-template.ftl">
----------------------------------------------------------------------------------------------------
</#list>
