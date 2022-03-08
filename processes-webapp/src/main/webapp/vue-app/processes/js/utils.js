export function htmlToText(htmlContent) {
  const tempDivElement = document.createElement('div');
  tempDivElement.innerHTML = htmlContent;
  return tempDivElement.textContent || tempDivElement.innerText || '';
}