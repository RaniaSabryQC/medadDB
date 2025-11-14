<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>

<@layout.registrationLayout displayMessage=!messagesPerField.existsError('existingAccountQuestionError'); section>
	<!-- template: idp-existing-account-question.ftl -->

	<#if section == "header">
		${msg("existingAccountQuestion.title")}

	<#elseif section = "subheader">
        ${msg("existingAccountQuestion.description", identityProvider.displayName!identityProvider.providerAlias, realm.displayName!)}

	<#elseif section == "form">
		<form id="kc-existing-account-question-form"
					class="${properties.kcFormClass!}"
					action="${url.loginAction}"
					method="post"
					novalidate="novalidate">

			<@buttons.actionGroup>
				<!-- YES button -->
				<@buttons.button
				id="existing-account-question-yes"
				name="existingAccountAnswer"
				value="yes"
				label="existingAccountQuestion.yes"
				class=["kcButtonClass", "kcButtonPrimaryClass", "kcButtonAlternativePrimaryClass", "kcMarginBottom1", "kcButtonBlockClass", "kcButtonLargeClass"]/>

				<#if registrationAllowed>
					<!-- NO button -->
					<@buttons.button
					id="existing-account-question-no"
					name="existingAccountAnswer"
					value="no"
					label="existingAccountQuestion.no"
					class=["kcButtonClass", "kcButtonSecondaryClass", "kcButtonAlternativeSecondaryClass", "kcButtonBlockClass", "kcButtonLargeClass"]/>
				</#if>

			</@buttons.actionGroup>

			<#if messagesPerField.existsError('existingAccountQuestionError')>
				<div class="${properties.kcFormGroupClass!}">
					<div class="${properties.kcInputWrapperClass!}">
            <span id="input-error-existingAccountQuestionError"
									class="${properties.kcInputErrorMessageClass!}"
									aria-live="polite">
              ${kcSanitize(messagesPerField.get('existingAccountQuestionError'))?no_esc}
            </span>
					</div>
				</div>
			</#if>

		</form>
	</#if>
</@layout.registrationLayout>
