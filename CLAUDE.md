# Claude Code — Project Rules

## Java

- **No fully qualified class names in code.** Always add a proper `import` statement at the top of the file. Never write `new jakarta.persistence.EntityNotFoundException(...)` or `jakarta.servlet.http.HttpServletRequest` inline — import the type and use the simple name.
