FROM eclipse-temurin:21
RUN apt update && \
	apt install -y --no-install-recommends \
	python3 \
	ffmpeg && \
	apt-get clean && \
	rm -rf /var/lib/apt/lists/*
RUN wget https://github.com/yt-dlp/yt-dlp/releases/download/2025.09.26/yt-dlp_linux && \
	mv yt-dlp_linux /usr/bin/yt-dlp && \
	chmod u+x /usr/bin/yt-dlp

