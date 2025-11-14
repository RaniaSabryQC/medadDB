(function () {
	const BODY_CLASS = "terms-modal-open";
	const TRIGGER_SELECTOR = "[data-kc-terms-trigger]";
	const MODAL_SELECTOR_TEMPLATE = "[data-kc-terms-modal='%PREFIX%']";
	const CLOSE_SELECTOR = "[data-kc-terms-close]";

	const modalStates = new WeakMap();

	const initModal = (modal) => {
		if (!modal) {
			return null;
		}

		let state = modalStates.get(modal);
		if (state) {
			return state;
		}

		const closeBtn = modal.querySelector(CLOSE_SELECTOR);
		if (!closeBtn) {
			return null;
		}

		const supportsDialog = typeof modal.showModal === "function";
		const prefix = modal.getAttribute("data-kc-terms-modal") || "";

		state = {
			supportsDialog,
			closeBtn,
			fallbackBackdrop: null,
			previouslyFocused: null,
		};

		if (!supportsDialog) {
			modal.setAttribute("data-dialog-fallback", "true");
			modal.setAttribute("role", "dialog");
			modal.setAttribute("aria-modal", "true");
			modal.style.display = "none";

			const backdrop = document.createElement("div");
			backdrop.className = "terms-modal-fallback-backdrop";
			backdrop.setAttribute("aria-hidden", "true");
			document.body.appendChild(backdrop);
			state.fallbackBackdrop = backdrop;
		}

		modal.setAttribute("hidden", "hidden");
		modal.setAttribute("aria-hidden", "true");

		const closeModal = () => {
			if (state.supportsDialog) {
				if (modal.open) {
					modal.close();
				}
			} else {
				modal.style.display = "none";
				if (state.fallbackBackdrop) {
					state.fallbackBackdrop.style.display = "none";
				}
			}

			modal.setAttribute("aria-hidden", "true");
			modal.setAttribute("hidden", "hidden");
			document.body.classList.remove(BODY_CLASS);

			const previouslyFocused = state.previouslyFocused;
			state.previouslyFocused = null;
			if (previouslyFocused && typeof previouslyFocused.focus === "function") {
				previouslyFocused.focus();
			}
		};

		const openModal = (event) => {
			if (event) {
				event.preventDefault();
			}

			state.previouslyFocused = document.activeElement;

			if (state.supportsDialog) {
				modal.removeAttribute("hidden");
				if (!modal.open) {
					modal.showModal();
				}
			} else {
				modal.style.display = "block";
				if (state.fallbackBackdrop) {
					state.fallbackBackdrop.style.display = "block";
				}
			}

			modal.setAttribute("aria-hidden", "false");
			document.body.classList.add(BODY_CLASS);

			if (typeof state.closeBtn.focus === "function") {
				try {
					state.closeBtn.focus({ preventScroll: true });
				} catch (_err) {
					state.closeBtn.focus();
				}
			}
		};

		state.closeModal = closeModal;
		state.openModal = openModal;

		state.closeBtn.addEventListener("click", (event) => {
			event.preventDefault();
			closeModal();
		});

		if (state.supportsDialog) {
			modal.addEventListener("cancel", (event) => {
				event.preventDefault();
				closeModal();
			});
			modal.addEventListener("click", (event) => {
				const rect = modal.getBoundingClientRect();
				const inDialog =
					rect.top <= event.clientY &&
					event.clientY <= rect.bottom &&
					rect.left <= event.clientX &&
					event.clientX <= rect.right;
				if (!inDialog) {
					closeModal();
				}
			});
		} else {
			modal.addEventListener("keydown", (event) => {
				if (event.key === "Escape" || event.key === "Esc") {
					closeModal();
				}
			});
			if (state.fallbackBackdrop) {
				state.fallbackBackdrop.addEventListener("click", closeModal);
			}
		}

		modalStates.set(modal, state);
		return state;
	};

	const bindTrigger = (trigger) => {
		if (trigger.dataset.kcTermsBound === "true") {
			return;
		}

		const prefix = trigger.getAttribute("data-kc-terms-trigger");
		if (!prefix) {
			return;
		}

		const selector = MODAL_SELECTOR_TEMPLATE.replace(
			"%PREFIX%",
			prefix.replace(/['"\\]/g, "\\$&")
		);
		const modal = document.querySelector(selector);
		const state = initModal(modal);

		if (!state) {
			return;
		}

		trigger.addEventListener("click", state.openModal);
		trigger.dataset.kcTermsBound = "true";
	};

	const initialize = () => {
		document.querySelectorAll(TRIGGER_SELECTOR).forEach(bindTrigger);
	};

	if (document.readyState === "loading") {
		document.addEventListener("DOMContentLoaded", initialize);
	} else {
		initialize();
	}
})();
