<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp'); section>
    <#if section = "header">
        ${msg("doLogIn")}
    <#elseif section = "form">
        <form id="kc-otp-login-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <#if otpLogin.userOtpCredentials?size gt 1>
                <div class="${properties.kcFormGroupClass!}">
                    <#list otpLogin.userOtpCredentials as otpCredential>
                        <input id="kc-otp-credential-${otpCredential?index}" class="${properties.kcLoginOTPListInputClass!}" type="radio" name="selectedCredentialId" value="${otpCredential.id}" <#if otpCredential.id == otpLogin.selectedCredentialId>checked="checked"</#if>>
                        <label for="kc-otp-credential-${otpCredential?index}" class="${properties.kcLoginOTPListClass!}">
                            <span class="${properties.kcLoginOTPListItemTitleClass!}">${otpCredential.userLabel}</span>
                        </label>
                    </#list>
                </div>
            </#if>
            <div class="${properties.kcFormGroupClass!}">
                <label for="otp" class="${properties.kcLabelClass!}">${msg("loginOtpOneTime")}</label>
                <input id="otp" name="otp" autocomplete="one-time-code" inputmode="numeric" type="text" class="${properties.kcInputClass!}"
                       autofocus aria-invalid="<#if messagesPerField.existsError('totp')>true</#if>" dir="ltr"/>
                <#if messagesPerField.existsError('totp')>
                    <span id="input-error-otp-code" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('totp'))?no_esc}
                    </span>
                </#if>
            </div>
            <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}"
                       name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
