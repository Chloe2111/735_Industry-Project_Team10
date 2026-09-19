# Story 17 – Community workspace skeleton

Implemented scope: UI from the existing wireframes, group and membership APIs, activity feed API, client/server input validation, API integration, loading/success/error handling with the shared toast provider, and Community-focused frontend/backend tests.

## Development routing
- Vite: http://localhost:5173
- Spring Boot: http://localhost:8080
- Vite proxies `/api` to Spring Boot during development.

## Community API
- `GET/POST /api/community/groups`
- `GET/PUT/DELETE /api/community/groups/{id}`
- `POST/GET /api/community/groups/{groupId}/members`
- `DELETE /api/community/groups/{groupId}/members/{userId}`
- `GET /api/community/users/{userId}/memberships`
- `GET/POST /api/community/posts`
- `GET /api/community/posts/group/{groupId}`
- `GET/DELETE /api/community/posts/{id}`

## Authentication boundary
The repository does not currently contain a shared authentication provider. CommunityWorkspace therefore uses one clearly marked temporary development identity (`community-demo-user`). Replace that constant with the authenticated principal/user context when the team's auth implementation lands. The Community UI does not claim production authentication is complete.

## Attachments
Story 17's Create Post UI validates attachment type and size, but binary upload/storage is intentionally not sent to the text-post endpoint because the repository has no agreed upload/storage API. This avoids silently discarding files or bypassing later governance/storage requirements.

## Verification
From `backend`: `mvn test`

From `frontend`: `npm install`, then `npm test` and `npm run build`.

## Group discovery / membership UI correction
- Groups page now separates **My Groups** from **Suggested Groups**.
- Join and Leave actions are available directly on group cards.
- The group filter supports All groups, My groups and Suggested groups.
- Membership state is loaded from `/api/community/users/{userId}/memberships` and refreshed locally after join/leave actions.
- Join/leave operations surface success/error toasts and disable the active action while it is processing.
