<#import "terms-modal.ftl" as termsModal>
<#import "policy-modal.ftl" as policyModal>

<#macro termsAcceptance>
    <#if termsAcceptanceRequired??>
        <div class="form-group">
            <div class="terms-acceptance-wrapper">
                <input type="checkbox" id="termsAccepted" name="termsAccepted" class="${properties.kcCheckboxInputClass!}"
                       aria-invalid="<#if messagesPerField.existsError('termsAccepted')>true</#if>"
                />
                <label for="termsAccepted" class="${properties.kcLabelClass!}">
                    ${msg("acceptAll")} 
                    <@termsModal.renderTermsModal prefix="registerTerms"
                        triggerLabel=msg("termsTitle") triggerClasses="terms-link-trigger" triggerAsLink=true/>
                    
                    ${msg("and")} 
                    <@policyModal.renderPolicyModal prefix="registerPolicy"
                        triggerLabel=msg("policyTitle") triggerClasses="terms-link-trigger" triggerAsLink=true/>
                </label>
            </div>
            <#if messagesPerField.existsError('termsAccepted')>
                <div class="${properties.kcLabelWrapperClass!}">
                            <span id="input-error-terms-accepted" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                                ${kcSanitize(messagesPerField.get('termsAccepted'))?no_esc}
                            </span>
                </div>
            </#if>
        </div>
    </#if>
</#macro>
