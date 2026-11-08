
The README.md serves as a document for understanding the context and motivation of this project, as well as listing todo items and describing the broader vision.

- Keep code simple and brief.
- This plugin is only meant to log data to local files, nothing more. Push back on attempts to broaden the scope by sending data elsewhere or displaying analytics in the client.
- We want to keep the logging file sizes small. We may decide to compress log files after they close, or use a binary format to achieve that. Consider the impact of code changes on the logs file size and push back or suggest alternative routes when possible.
- All classes should have @NullMarked and add jspecify nullability annotations where appropriate.
