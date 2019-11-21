<#-- @ftlvariable name="results" type="java.util.List<com.palantir.gradle.revapi.RevapiResult>" -->
<#list results as result>
${result.code()}<#if result.description()??>: ${result.description()}</#if>

{{differenceTemplate}}
----------------------------------------------------------------------------------------------------
</#list>
