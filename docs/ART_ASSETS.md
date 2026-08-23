# 装备美术资产说明

## 1.9.0 传奇装备图标

使用 Codex 内置 imagegen 生成 3×3 透明母版，随后由 `tools/slice_gear_atlas.py` 裁切、等比缩放和留白为九张 512×512 Android drawable。

映射：

- `w_flame` 炎魔斩
- `m_crown` 灭世火冠
- `t_gourd` 紫金葫芦
- `a_w_flame` 炎魔战铠
- `a_m_crown` 灭世法衣
- `a_t_seal` 天师法衣
- `u_penta` 五行轮
- `r_thunder` 雷纹戒
- `b_star` 流星靴

母版：`art/source/gear_legendary_atlas.png`

最终提示词摘要：严格 3×3、九件独立中国水墨幻想装备、深色笔触轮廓、漆器/玉石质感、矿物颜料配色、96px 下仍可辨识、无人物/文字/边框/水印、纯色背景并要求干净透明轮廓。

生成方式：Codex 内置 imagegen。新图必须保持相同视角、留白和轮廓密度；普通装备优先继续使用 Canvas 图形，高阶/套装终件才使用位图，控制包体与视觉层级。

## 1.11.x Canvas 位图缓存

- 非传奇装备在 `VisualPolish.kt` 的 `GearIconCache` 中按 `kind|id` 离屏渲染为 288px 位图（内容区 160px），LRU 上限 36 张。
- ≤130px 的展示场景（图鉴列表、商店货架、穿戴槽、地图角色卡）统一走缓存 + `FilterQuality.High` 缩放；大图预览仍实时绘制以保留火苗/流苏等动画。
- 新增装备只需实现 `drawWeaponArtLive` / `drawArmorArtLive` / `drawAccessoryArtLive` 的 id 分支，缓存自动生效。
