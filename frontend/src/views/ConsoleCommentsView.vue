<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>评论</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div class="console-empty" style="padding: 28px 16px">
        <p class="console-prose" style="margin: 0; text-align: center; max-width: 520px">
          当前后端提供按文章维度的评论列表与删除接口（需登录）。全站评论聚合与审核后台尚未提供，请在文章详情页或后续扩展接口后在此对接。
        </p>
        <ul class="console-api-hint">
          <li><code>GET /api/v1/articles/{id}/comments</code> 分页列表</li>
          <li><code>DELETE /api/v1/comments/{id}</code> 删除（作者或管理员）</li>
        </ul>
      </div>
    </div>
  </div>
</template>
