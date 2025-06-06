# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build and Package
- `just build` - Build the Scala application and create Docker image using scala-cli
- `just publish` - Build and push to ghcr.io/fazzou/ydr:latest  
- `just deploy` - Publish and deploy to remote server (requires REMOTE_HOST and REMOTE_PATH env vars)

### Running the Application
- `scala-cli run .` - Run the application locally
- `docker compose up -d` - Run using Docker Compose (see docker-compose.example.yml)

### Compilation and Testing
- Use `mcp__metals-ydr__compile-full` to compile the entire project
- Use `mcp__metals-ydr__compile-file` to compile specific files
- Use `mcp__metals-ydr__test` to run tests

## Architecture Overview

This is a YouTube podcast downloader web application built with Scala 3, using:

- **Tapir** for HTTP server endpoints
- **OX** for structured concurrency and actor-based state management
- **Scalatags** for HTML generation
- **yt-dlp** external tool for downloading content
- **OpenAI API** for AI-generated podcast cover images

### Core Components

1. **YdrServer** (`server.scala`) - Main HTTP server with endpoints:
   - `/` - Main dashboard showing all podcast directories
   - `/add` - Add new podcast feed
   - `/resync` - Manually trigger synchronization 
   - `/prompt` - Edit AI image generation prompt
   - `/state-list` - Get current state as HTML fragment

2. **Runner** (`runner.scala`) - Background processor that:
   - Downloads podcasts using yt-dlp with configurable flags
   - Processes directories containing yt-dlp.conf files
   - Runs on configurable intervals (INTERVAL env var)
   - Updates state through StateActor

3. **StateActor** (`state.scala`) - Manages application state:
   - Tracks directory synchronization states (NotSynchronized, Synchronizing, Synchronized)
   - Thread-safe state updates using actor pattern

4. **ImgGen** (`imggen.scala`) - AI cover image generation:
   - Uses OpenAI GPT-4 to analyze podcast titles and create image prompts
   - Generates cover images with DALL-E 3
   - Supports custom prompt templates with mustache templating

5. **Page** (`page.scala`) - HTML rendering with HTMX:
   - Server-side rendering using Scalatags
   - Real-time updates via HTMX polling
   - Responsive design with Tailwind CSS

### Data Flow

1. User adds new podcast via web form → creates directory with yt-dlp.conf
2. Runner processes all directories periodically or on demand
3. yt-dlp downloads audio files and metadata to each directory  
4. ImgGen creates AI-generated cover images based on episode titles
5. StateActor tracks sync status, displayed in real-time web UI

### Environment Configuration

- `DATA_DIR` - Base directory for podcast storage (default: /data)
- `PORT` - HTTP server port (default: 80)
- `INTERVAL` - Sync interval in ISO-8601 duration format (e.g. PT8H for 8 hours)
- `COMMON_FLAGS` - yt-dlp command line flags
- `OPENAI_TOKEN` - Required for AI image generation feature

### Key Design Patterns

- Actor-based concurrency for thread-safe state management
- Structured concurrency with OX for resource safety
- Functional error handling with Either types
- Template-based HTML generation with type safety

## GitHub Repository Setup

For the GitHub Actions workflows to work properly, ensure:

1. **Repository Settings > Actions > General**:
   - Allow "Read and write permissions" for GITHUB_TOKEN
   - Allow GitHub Actions to create and approve pull requests

2. **Repository Settings > Code and automation > Actions > General**:
   - Workflow permissions: "Read and write permissions"
   - Check "Allow GitHub Actions to create and approve pull requests"

3. **Package Registry Access**:
   - GitHub Container Registry (ghcr.io) should be accessible
   - Repository visibility affects package permissions