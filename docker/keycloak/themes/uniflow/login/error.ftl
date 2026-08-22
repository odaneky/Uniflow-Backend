<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${kcSanitize(msg("errorTitle"))?no_esc}
    <#elseif section = "form">
        <div id="kc-error-message">
            <p class="uf-lede">${kcSanitize(message.summary)?no_esc}</p>
            <#if skipLink??>
            <#else>
                <#if client?? && client.baseUrl?has_content>
                    <p><a id="backToApplication" href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
                <#else>
                    <p><a href="${url.loginUrl}">${msg("backToLogin")}</a></p>
                </#if>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
