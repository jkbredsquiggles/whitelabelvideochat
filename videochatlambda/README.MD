
# Video Chat App Server

A AWS lambda based chat server which maintains a websocket connection with clients via the AWS gateway, integrating with `videochatlibrary` to handle video chat data.

Similar to chat lambda but uses a re-factored framework.


# Operations notes

- run the cloud formation stack
- not sure whether the stage needs to be deployed, testing
- update site config with the api gateway url
- Need API key, app key, signing key - see jitsi in key chain for signing key - required for stack creation


