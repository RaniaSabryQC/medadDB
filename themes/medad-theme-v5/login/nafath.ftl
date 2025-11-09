<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "form">
			<label>
				Enter Your ID:
				<input id="id" name="id" type="text">
			</label>
			<div id="kc-form-buttons" class="form-group">
				<button id="kc-login" class="pf-c-button pf-m-primary pf-m-block btn-lg" onclick="onIDSubmit()"> Submit </button>
			</div>
			<div>
				<label>
					Verification Number:
					<span id="verification-number" ></span>
				</label>
			</div>
			<form id="session-form" action="/realms/${realm.name}/broker/nafath/endpoint" method="post">
				<input type="hidden" id="nafath-user-id" name="id">
				<input type="hidden" id="nafath-trans-id" name="trans_id">
				<input type="hidden" id="nafath-random" name="random">
				<input type="hidden" id="nafath-code" name="session_code">
				<input type="hidden" id="nafath-tab-id" name="tab_id">
				<input type="hidden" id="nafath-client-id" name="client_id">
			</form>
			<script>
				let interval;
				function onIDSubmit() {
					console.log("Submitting ..................................");
					let id = document.getElementById("id").value;
					console.log("Submitting this id " + id);
					fetch("/realms/${realm.name}/broker/nafath/endpoint/transactions",
						{
							headers: {
								'Accept': 'application/json',
								'Content-Type': 'application/json'
							},
							method: "POST",
							body: JSON.stringify({ id: id })
						}).then(async response => {
						let body = await response.json()
						body.id = id;
						console.log("Got this response for trans ", body);
						document.getElementById("verification-number").innerText = body["random"];
						interval = setInterval(() => checkTransactionStatus(body), 2000);
					})
						.catch(console.error);
				}

				function checkTransactionStatus(params) {
					fetch("/realms/${realm.name}/broker/nafath/endpoint/status",
						{
							headers: {
								'Accept': 'application/json',
								'Content-Type': 'application/json'
							},
							method: "POST",
							body: JSON.stringify(params)
						}).then(async response => {
						let body = await response.json()
						if (body.status !== "WAITING") {
							clearInterval(interval);
							createSession(params);
						}
					})
						.catch(console.error);
				}


				function createSession(nafathStatusMap) {
					const urlParams = new URLSearchParams(window.location.search);
					document.getElementById("nafath-client-id").value = urlParams.get("client_id");
					document.getElementById("nafath-tab-id").value = urlParams.get("tab_id");
					document.getElementById("nafath-code").value = urlParams.get("session_code");
					document.getElementById("nafath-user-id").value = nafathStatusMap["id"];
					document.getElementById("nafath-trans-id").value = nafathStatusMap["transId"];
					document.getElementById("nafath-random").value = nafathStatusMap["random"];
					document.getElementById("session-form").submit();
				}
			</script>
    </#if>

</@layout.registrationLayout>
