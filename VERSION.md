# ChatArchive Version 0.6.0

**A product of Elimara Technologies Limited**  
Created by Basil Okoth.

## Included in this consolidated release

### Conversation attachment archive
- attachment cards inside archived conversations
- encrypted attachment metadata
- private local retention of notification-exposed files
- document, image, audio and video support
- Open, Save, Share and Export actions
- attachment ZIP export with message context
- truthful unavailable status when Android/WhatsApp does not expose file bytes
- FileProvider-based sharing without all-files access

### Google Play global payments
- Free + Lifetime Pro model
- one-time product ID: `chatarchive_pro_lifetime`
- Google Play localized pricing
- purchase restoration
- pending/cancelled purchase handling
- cached offline Pro entitlement
- old signed lifetime licences grandfathered into Pro
- encrypted backup, restore/import and JSON export gated as Pro tools

### Company branding
- customer-facing ownership: Elimara Technologies Limited
- creator credit retained for Basil Okoth
- export metadata identifies Elimara Technologies Limited as the product company

## Important attachment limitation
ChatArchive can preserve actual attachment bytes only when Android/WhatsApp exposes a
readable media URI to the notification listener. If a notification exposes only a label
such as “Photo”, “Document” or “Voice message”, the app cannot reconstruct bytes that
were never made available.

## Release
- versionCode: 13
- versionName: 0.6.0
