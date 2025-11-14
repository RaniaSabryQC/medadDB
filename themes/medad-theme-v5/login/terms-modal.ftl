<#macro renderTermsModal prefix triggerLabel="" triggerClasses="" triggerAsLink=false>
    <#assign localTriggerLabel = triggerLabel!msg("viewFullTerms")!"View Full Terms & Conditions" />
    <#assign triggerId = "${prefix}OpenTermsTrigger" />

    <#if triggerAsLink>
        <a href="#" class="${triggerClasses}"
           id="${triggerId}" role="button" aria-haspopup="dialog" aria-controls="${prefix}TermsModal"
           data-kc-terms-trigger="${prefix}">
            ${localTriggerLabel}
        </a>
    <#else>
        <button type="button" class="view-full-terms-btn ${triggerClasses}"
                id="${triggerId}" aria-haspopup="dialog" aria-controls="${prefix}TermsModal"
                data-kc-terms-trigger="${prefix}">
            ${localTriggerLabel}
        </button>
    </#if>

    <dialog id="${prefix}TermsModal" class="terms-modal" aria-labelledby="${prefix}ModalTitle" aria-describedby="${prefix}ModalDesc"
            data-kc-terms-modal="${prefix}" aria-hidden="true" hidden>
        <div class="terms-modal-content">
            <button type="button" class="terms-modal-close" id="${prefix}CloseModal" aria-label="Close"
                    data-kc-terms-close>
                &times;
            </button>
            <h2 id="${prefix}ModalTitle">${msg("termsTitle")}</h2>
            <div id="${prefix}ModalDesc" class="terms-modal-body">
                ${kcSanitize(msg("termsText"))?no_esc}
            </div>
        </div>
    </dialog>
</#macro>
