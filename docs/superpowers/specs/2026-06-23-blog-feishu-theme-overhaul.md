# Blog Public Area — Feishu Blue-White Theme Overhaul

**Date:** 2026-06-23
**Status:** Approved
**Branch:** feat/feishu-theme-overhaul

## Summary

Overhaul the public blog area's visual design from purple-accented glassmorphism to a Feishu-inspired blue-white professional aesthetic. This is a visual-only change — no layout structure or functional behavior is modified.

## Scope

### In Scope

Public blog area only — the visitor-facing portion of the site:

- CSS design tokens (`:root` variables in blog.css)
- Navigation bar (BlogHeader.vue)
- Home page (HomeView.vue)
- Article cards (ArticleCard.vue, home-post-card)
- Article detail page (ArticleDetailView.vue)
- All articles page (AllArticlesView.vue)
- About page (AboutView.vue)
- Feedback page (FeedbackView.vue)
- Changelog pages (ChangelogListView.vue, ChangelogDetailView.vue)
- Profile card sidebar (ProfileCard.vue)
- Right dock (RightDock.vue)
- Welcome gate dialog (WelcomeGate.vue)
- Comment section (CommentSection.vue, CommentItem.vue)
- Back to top button (BackToTop.vue)
- Blog info card (BlogInfoCard.vue)
- Global styles in blog.css and style.css

### Out of Scope

- Admin console (`/console` routes, ConsoleLayout.vue, all Console*.vue pages) — stays as-is
- Login/register page (SiteAuthView.vue) — already redesigned in previous task
- Admin login page (AdminLoginView.vue)
- Any behavioral/functional changes
- Dark mode functionality (stays as-is, only color values change)

## Design Tokens Change

### CSS Variables — blog.css `:root`

```css
/* Before */
--bg: #ffffff;
--text: #2c2c2c;
--muted: #7a7a7a;
--border: rgba(0, 0, 0, 0.06);
--accent: #aa3bff;
--accent2: #4aa3ff;
--surface: rgba(255, 255, 255, 0.78);
--surface-soft: rgba(255, 255, 255, 0.52);
--placeholder-tint: #dddddd;
--card: rgba(255, 255, 255, 0.52);

/* After */
--bg: #f5f6f7;
--text: #1f2329;
--muted: #8f959e;
--border: #e5e6e8;
--accent: #3370ff;
--accent2: #2860e0;
--surface: #ffffff;
--surface-soft: #f5f6f7;
--placeholder-tint: #e5e6e8;
--card: #ffffff;
```

### CSS Variables — blog.css `[data-theme='dark']`

```css
/* Before */
--bg: #121212;
--text: #e8e8ea;
--muted: #9a9a9a;
--border: rgba(255, 255, 255, 0.12);
--accent: #b366ff;
--accent2: #5eb0ff;
--surface: rgba(42, 42, 42, 0.72);
--surface-soft: rgba(34, 34, 36, 0.48);
--placeholder-tint: #444444;
--code-bg: #252528;
--text-h: #e8e8ea;
--card: rgba(28, 28, 30, 0.55);

/* After */
--bg: #0f1114;
--text: #e5e7eb;
--muted: #9ca3af;
--border: #2a2a2e;
--accent: #4a7fff;
--accent2: #3370ff;
--surface: #1a1a1a;
--surface-soft: #16181d;
--placeholder-tint: #2a2a2e;
--code-bg: #1a1a1a;
--text-h: #e5e7eb;
--card: #1a1a1a;
```

## Component-Level Changes

### 1. Navigation Bar (BlogHeader.vue)

| Element | Before | After |
|---------|--------|-------|
| Background | `#ffffff` solid | `#ffffff` (unchanged) |
| Border | `rgba(0,0,0,0.06)` | `#e5e6e8` (solid) |
| Height | auto (padding-based) | 56px fixed |
| Nav link color | `--muted` (#7a7a7a) | #646a73 |
| Nav link active | `::after` bottom bar | `color: #3370ff` + `border-bottom: 2px solid #3370ff` |
| Nav link spacing | 22px gap | 0 gap, 16px horizontal padding per item |
| Auth pills | `border-radius: 999px` | `border-radius: 6px` |
| Login button | ghost pill | outlined `border: 1px solid #dee0e3` |
| Register button | filled pill (rgba) | solid blue `background: #3370ff; color: #fff` |

### 2. Home Page (HomeView.vue)

| Element | Before | After |
|---------|--------|-------|
| Page background | `#ffffff` | `#f5f6f7` |
| Hero title size | 60px | 42px |
| Hero title weight | 500 | 600 |
| Hero title color | `--text` (#2c2c2c) | `#1f2329` |
| Hero subtitle size | 20px | 16px |
| Hero subtitle color | `--muted` (#7a7a7a) | `#8f959e` |
| Featured card border-radius | 18px | 10px |
| Featured card background | `rgba(255,255,255,0.75)` | `#ffffff` |
| Post card border-radius | 16px | 10px |
| Post card background | `rgba(255,255,255,0.75)` | `#ffffff` |
| Post card border | `1px solid var(--border)` | `1px solid #e5e6e8` |
| Post card hover border | `rgba(170,59,255,0.25)` | `#3370ff` |
| Post card hover shadow | `0 10px 26px rgba(0,0,0,0.06)` | `0 4px 16px rgba(51,112,255,0.1)` |
| Post card hover translateY | -2px | none (box-shadow only) |
| Newsletter background | `rgba(0,0,0,0.03)` | `#f5f6f7` |
| Newsletter border-radius | 22px | 10px |
| Newsletter button | `#111` (black) | `#3370ff` (blue) |

### 3. Article Cards (ArticleCard.vue)

| Element | Before | After |
|---------|--------|-------|
| Background | `var(--card)` translucent | `#ffffff` solid |
| Border | `1px solid var(--border)` | `1px solid #e5e6e8` |
| Border-radius | 14px | 10px |
| Title size | 20px | 15px |
| Title weight | 500 | 600 |
| Cover placeholder | `#e9e9e9` | `#e5e6e8` |

### 4. Article Detail Page (ArticleDetailView.vue)

| Element | Before | After |
|---------|--------|-------|
| Title weight | 900 | 700 |
| Title color | `--text` | `#1f2329` |
| Muted color | `--muted` (#7a7a7a) | `#8f959e` |
| Cover border-radius | 18px | 10px |
| Cover max-height | `min(52vh, 440px)` | unchanged |

### 5. Profile Card (ProfileCard.vue)

| Element | Before | After |
|---------|--------|-------|
| Card background | `var(--card)` translucent + blur | `#ffffff` solid |
| Backdrop filter | `blur(18px)` | none |
| Avatar border | `4px solid var(--card)` | `4px solid #ffffff` |
| Name weight | 950 | 700 |
| Menu links bg | `#111` (black) | `#3370ff` (blue) |
| Menu links text | `#fff` | `#fff` |
| Menu links border-radius | 10px | 8px |
| Social links bg | `#111` (black) | `#3370ff` (blue) |
| Icon link bg | `#111` | `#3370ff` |

### 6. Right Dock (RightDock.vue)

| Element | Before | After |
|---------|--------|-------|
| Background | `var(--card)` translucent + blur | `#ffffff` solid |
| Backdrop filter | `blur(18px)` | none |
| Border | `1px solid var(--border)` | `1px solid #e5e6e8` |
| Button border-radius | 10px | 8px |

### 7. Comment Section (CommentSection.vue)

| Element | Before | After |
|---------|--------|-------|
| Card background | `var(--card)` translucent | `#ffffff` solid |
| Border | `1px solid var(--border)` | `1px solid #e5e6e8` |
| Reply left border | `2px solid rgba(170,59,255,0.18)` | `2px solid rgba(51,112,255,0.18)` |
| Comment avatar border | `1px solid var(--border)` | `1px solid #e5e6e8` |
| Delete hover | `#b42318` | unchanged |

### 8. Welcome Gate (WelcomeGate.vue)

| Element | Before | After |
|---------|--------|-------|
| Illustration bg | purple gradient | blue gradient `#3370ff → #e6f0ff` |
| Overlay bg | `rgba(0,0,0,0.45)` | unchanged |
| Enter button hover shadow | `rgba(170,59,255,0.35)` | `rgba(51,112,255,0.35)` |

### 9. Global Changes

| Element | Before | After |
|---------|--------|-------|
| `.card` border | `1px solid var(--border)` | `1px solid #e5e6e8` |
| `.card` background | `var(--card)` translucent | `#ffffff` |
| `.card` border-radius | 14px | 10px |
| `.card` backdrop-filter | blur(18px) | none |
| `.btn.primary` background | `linear-gradient(135deg, var(--accent), var(--accent2))` | `#3370ff` |
| `.btn.primary` border-radius | 999px | 8px |
| `.btn` border-radius | 999px | 8px |
| `.btn` background | `var(--surface)` | `#ffffff` |
| `.input`/`.textarea` border-radius | 12px | 8px |
| `.input`/`.textarea` backdrop-filter | blur(12px) | none |
| `.input:focus`/`.textarea:focus` border-color | `rgba(170,59,255,0.45)` | `#3370ff` |
| `.input:focus`/`.textarea:focus` box-shadow | `0 0 0 4px rgba(170,59,255,0.08)` | `0 0 0 3px rgba(51,112,255,0.12)` |
| `.tab` border-radius | 999px | 8px |
| `.tab.active` border-color | `rgba(170,59,255,0.45)` | `#3370ff` |
| `.tab.active` background | `rgba(170,59,255,0.08)` | `rgba(51,112,255,0.08)` |
| `.error` background | `rgba(180,35,24,0.08)` | unchanged |
| `.error` border | `rgba(180,35,24,0.25)` | unchanged |
| `.sidebar-item.active` border-color | `rgba(170,59,255,0.55)` | `#3370ff` |
| `.sidebar-item.active` box-shadow | `rgba(170,59,255,0.14)` | `rgba(51,112,255,0.14)` |
| `.sidebar-item.active` background | `rgba(170,59,255,0.06)` | `rgba(51,112,255,0.06)` |
| `.search-input:focus` border-color | `rgba(170,59,255,0.45)` | `#3370ff` |
| `.search-input:focus` box-shadow | `rgba(170,59,255,0.08)` | `rgba(51,112,255,0.08)` |
| Site top bar background | `#ffffff` | unchanged |
| Console sidebar (CSS vars) | `--console-*` family | unchanged (out of scope) |
| Auth split CSS (`.auth-*` classes) | Already Feishu-style | unchanged |

## Glassmorphism Removal

Remove all `backdrop-filter` and `-webkit-backdrop-filter` declarations from:

- `.card`
- `.search-input`, `.icon-link`, `.btn:not(.primary)`, `.input`, `.textarea`
- `.tab`, `.avatar-preview`, `.article-list-item`, `.cover-preview`
- `.article-list`, `.article-editor`
- `.theme-toggle`, `.back-to-top-btn`
- `.right-dock`
- `.articles-drawer`, `.widgets-drawer`

## Files to Change

| File | Action |
|------|--------|
| `vue/src/assets/blog.css` | Major — update variables, global classes, components |
| `vue/src/assets/style.css` | Minor — update `#app` styling if needed |
| `vue/src/components/BlogHeader.vue` | If any inline styles exist |
| `vue/src/components/WelcomeGate.vue` | Update gradient colors in scoped style |
| `vue/src/components/ProfileCard.vue` | Minor — verify no hardcoded colors |
| `vue/src/components/RightDock.vue` | Minor — verify no hardcoded colors |

All other Vue components should work unchanged if they use CSS variables and classes from blog.css.

## Tech Stack

- Vue 3 Composition API
- Plain CSS (no UI framework)
- No new dependencies
