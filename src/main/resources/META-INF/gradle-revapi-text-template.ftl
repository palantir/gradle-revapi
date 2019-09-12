<#-- @ftlvariable name="reports" type="java.util.Collection<org.revapi.Report>" -->
<#-- @ftlvariable name="analysis" type="org.revapi.AnalysisContext" -->
<#list reports as report>
<#list report.differences as diff>
${diff.code}<#if diff.description??>: ${diff.description}</#if>

{{differenceTemplate}}
----------------------------------------------------------------------------------------------------
</#list>
</#list>
