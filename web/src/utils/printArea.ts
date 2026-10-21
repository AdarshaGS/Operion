/** Prints just the element(s) marked with the "print-area" class (see index.css), then restores the page. */
export function printArea(): void {
	document.body.classList.add("printing-area");
	const restore = () => {
		document.body.classList.remove("printing-area");
		window.removeEventListener("afterprint", restore);
	};
	window.addEventListener("afterprint", restore);
	window.print();
	// Some browsers (and jsdom) never fire afterprint; print() blocks until the dialog closes, so restoring here is safe too.
	restore();
}
