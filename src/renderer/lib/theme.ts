/** Follows the system's light or dark appearance; HeroUI switches on the `dark` class. */
export function syncColorScheme() {
  const query = window.matchMedia('(prefers-color-scheme: dark)');
  const apply = () => document.documentElement.classList.toggle('dark', query.matches);
  apply();
  query.addEventListener('change', apply);
}
