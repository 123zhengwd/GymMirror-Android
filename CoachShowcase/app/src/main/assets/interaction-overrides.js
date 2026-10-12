// Touch behavior for the packaged portrait display. The source H5 is shared
// with the venue screen, so keep device-only gestures here.
(() => {
  if (window.__coachShowcaseInteractionsInstalled) return;
  window.__coachShowcaseInteractionsInstalled = true;

  const style = document.createElement('style');
  style.textContent = `
    .carousel, .carousel *, .nfc-callout, .nfc-callout * {
      -webkit-tap-highlight-color: transparent !important;
      -webkit-user-select: none !important;
      user-select: none !important;
    }
    .carousel { touch-action: pan-y; }
    .member-card:focus, .member-card:focus-visible { outline: none !important; }
    .nfc-callout.coach-showcase-static {
      pointer-events: none !important;
      cursor: default !important;
    }
  `;
  document.head.appendChild(style);

  // The displayed NFC prompt is an instruction, not a simulated NFC trigger.
  // Keep the real NFC notification path in the bundled page unchanged.
  const updateTouchPrompts = () => {
    document.querySelectorAll('.nfc-callout').forEach((button) => {
      const isTouchPrompt = button.textContent.includes('碰一下');
      button.classList.toggle('coach-showcase-static', isTouchPrompt);
      if (isTouchPrompt) {
        button.setAttribute('aria-disabled', 'true');
        button.tabIndex = -1;
      } else {
        button.removeAttribute('aria-disabled');
        button.removeAttribute('tabindex');
      }
    });
  };
  updateTouchPrompts();
  new MutationObserver(updateTouchPrompts).observe(document.body, {
    childList: true,
    characterData: true,
    subtree: true,
  });
  document.addEventListener('click', (event) => {
    if (!(event.target instanceof Element)) return;
    const button = event.target.closest('.nfc-callout');
    if (!button || !button.textContent.includes('碰一下')) return;
    event.preventDefault();
    event.stopImmediatePropagation();
  }, true);

  let touchStart = null;
  let suppressClickUntil = 0;

  document.addEventListener('touchstart', (event) => {
    const target = event.target;
    if (event.touches.length !== 1 || !(target instanceof Element) ||
        !target.closest('.carousel')) {
      touchStart = null;
      return;
    }
    const touch = event.touches[0];
    touchStart = { x: touch.clientX, y: touch.clientY };
  }, { capture: true, passive: true });

  document.addEventListener('touchend', (event) => {
    if (!touchStart || event.changedTouches.length !== 1) return;
    const touch = event.changedTouches[0];
    const dx = touch.clientX - touchStart.x;
    const dy = touch.clientY - touchStart.y;
    touchStart = null;
    if (Math.abs(dx) < 48 || Math.abs(dx) <= Math.abs(dy) * 1.25) return;

    suppressClickUntil = performance.now() + 700;
    if (event.cancelable) event.preventDefault();
    window.dispatchEvent(new KeyboardEvent('keydown', {
      key: dx < 0 ? 'ArrowRight' : 'ArrowLeft',
      bubbles: true,
    }));
  }, { capture: true, passive: false });

  document.addEventListener('touchcancel', () => {
    touchStart = null;
  }, { capture: true, passive: true });

  document.addEventListener('click', (event) => {
    if (performance.now() >= suppressClickUntil ||
        !(event.target instanceof Element) ||
        !event.target.closest('.carousel')) return;
    event.preventDefault();
    event.stopImmediatePropagation();
  }, true);
})();
