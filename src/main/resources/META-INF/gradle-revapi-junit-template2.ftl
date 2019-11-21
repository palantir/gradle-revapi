<#ftl output_format="XML">
<#-- @ftlvariable name="results" type="com.palantir.gradle.revapi.RevapiResults" -->
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<testsuites failures="1" id="project-name" name="project-name" tests="1" time="0.000">
    <testsuite failures="1" id="${results.archiveNames()}" name="${results.archiveNames()}" tests="1" time="0.000">
        <#list results.results() as result>
        <testcase id="${result.code()}-${result.oldElement()!(result.newElement()!"<none>")}" name="Revapi Java API/ABI compatibility checker - ${result.code()}">
            <failure message="${result.oldElement()!(result.newElement()!"<none>")}<#if result.description()??>: ${result.description()}</#if>"><![CDATA[
{{differenceTemplate}}
            ]]></failure>
        </testcase>
        </#list>
    </testsuite>
</testsuites>
