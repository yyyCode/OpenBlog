# Login/Register Page Redesign — Feishu Style

**Date:** 2026-06-23
**Status:** Approved

## Summary

Redesign the site login/register page (`SiteAuthView.vue`) from a centered card + glassmorphism style to a Feishu (Lark) inspired left-right split layout with clean, professional aesthetics.

## Current State

- File: `vue/src/views/SiteAuthView.vue`
- Route: `/login` (with optional `?tab=register` / `?tab=change-password`)
- Layout: centered card (~520px) with glassmorphism backdrop, purple/gray gradient primary button, pill-shaped tabs
- Color scheme: purple accent (#aa3bff), dark gray buttons
- Register flow: shows "注册的账号需要管理员审核通过" message → after registration, prompts user to wait for approval → redirects to login tab

## Target Design

### Layout: Left-Right Split

```
┌──────────────────────┬──────────────────────────────┐
│                      │  ─── 登录  ─  注册  ─ 修改密码  │
│    Brand Panel       │                              │
│    (340px)           │    Form Area                 │
│    Gradient BG       │    (440px)                   │
│    Blue → Purple     │                              │
│                      │                              │
└──────────────────────┴──────────────────────────────┘
```

### Visual Specs

| Element | Before | After |
|---------|--------|-------|
| Layout | Centered card (520px) | Split panel (780px total) |
| Left panel | None | 340px brand area, gradient background |
| Right panel | Card body | 440px form area, white background |
| Primary color | #aa3bff (purple) | #3370ff (Feishu blue) |
| Button shape | Pill (border-radius: 999px) | Rounded (border-radius: 8px) |
| Button color | Gradient #3d3d3d→#1f1f1f | Solid #3370ff |
| Input background | White with blur | #f5f6f7 (light gray), white on focus |
| Input border-radius | 12px | 8px |
| Tab style | Pill buttons | Underline tabs |
| Tab active indicator | Purple pill background | 2px blue bottom border |
| Background | Transparent (depends on page) | #f0f2f5 (light gray solid) |
| Card shadow | Subtle blur | box-shadow 0 2px 20px rgba(0,0,0,0.06) |
| Error style | Red background box | Light red bg (#fff2f0) + border (#ffccc7) |
| Success style | Toast message | Light green bg (#f6ffed) + border (#b7eb8f) |
| Glassmorphism | Yes (backdrop-filter: blur) | No (pure solid colors) |

### Brand Panel (Left Side)

- Fixed width: 340px
- Background: linear-gradient(160deg, #3370ff, #5b47b0)
- Content: Logo icon, "OpenBlog" title, subtitle text, copyright footer
- Context-aware subtitles per tab:
  - Login: "一个简洁的写作与分享空间 / 记录技术笔记与生活思考"
  - Register: "加入 OpenBlog / 创建账号即可发表评论、点赞与收藏"
  - Change Password: "重置密码 / 输入注册邮箱验证后设置新密码"

### Form Area (Right Side)

- Flex width, min 0, padding 48px 44px
- Underline tabs: `display: flex; gap: 32px; border-bottom: 1px solid #ebecef`
  - Active: `font-weight: 600; color: #1f2329; border-bottom: 2px solid #3370ff`
  - Inactive: `font-weight: 400; color: #8f959e`
- Labels: `font-size: 13px; color: #646a73; font-weight: 500`
- Inputs: `background: #f5f6f7; border: 1px solid #dee0e3; border-radius: 8px`
- Focus state: `border-color: #3370ff; background: #fff`
- Submit button: `background: #3370ff; color: #fff; border-radius: 8px; height: 40px`
- Footer links: `color: #3370ff` for primary actions, `color: #8f959e` for secondary

### Behavior Changes

1. **Registration flow**: After successful registration, the API now returns `AuthResponse` (accessToken + refreshToken). Store tokens in localStorage and redirect to home page (`/`). Remove the "等待管理员审核" message and the manual tab-switch after registration.

2. **Error display**: Inline error messages use the new Feishu-style alert box instead of the current `.error` class.

3. **Responsive**: On screens ≤ 640px, collapse to single-column layout (hide brand panel, show form only).

### Theme Compatibility

Both light and dark themes must be supported:
- Dark mode left panel: background stays the same gradient (brand color)
- Dark mode form area: background #1a1a1a, input bg #2a2a2a, text #e5e7eb
- Dark mode error: background #2c1618, border #5c2022, text #f5c6cb
- Dark mode success: background #162c18, border #2c5c20, text #c6f5ca

## Files to Change

1. `vue/src/views/SiteAuthView.vue` — Complete template and script rewrite
2. `vue/src/assets/blog.css` — Add/update relevant CSS classes (no removal of existing classes used elsewhere)
3. `vue/src/api/admin.js` — No change needed (register already returns response data)

## Out of Scope

- `AdminLoginView.vue` (console admin login) — keep as-is
- Slider captcha integration — keep as-is (hidden from visual design, continues to work in the background)
- Email validation rules — unchanged

## Tech Stack

- Vue 3 Composition API (`<script setup>`)
- Vue Router (keep existing route `/login` with `?tab=` query)
- Plain CSS (no UI framework; project uses hand-written CSS)
