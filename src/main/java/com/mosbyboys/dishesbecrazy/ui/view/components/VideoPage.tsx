import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "./Button";
import Heading from "./Heading";
import "../styles/AlternatePath.css";

type VideoPageProps = {
    title: string;
    videoSrc: string | undefined;
    expectedFile: string;
};

// Shared layout for the video pages (AlternatePath, BackgroundRedirect): a title and a video that plays with sound.
export default function VideoPage({ title, videoSrc, expectedFile }: VideoPageProps) {
    const navigate = useNavigate();
    const videoRef = useRef<HTMLVideoElement>(null);
    const [needsTapForSound, setNeedsTapForSound] = useState(false);

    // Try to play with sound. Browsers block unmuted autoplay without a recent user gesture
    // (e.g. visiting the page's URL directly), so fall back to muted playback with a "tap for sound" button.
    useEffect(() => {
        const video = videoRef.current;
        if (!video) return;

        video.muted = false;
        video.volume = 1;
        video.play().catch(() => {
            video.muted = true;
            setNeedsTapForSound(true);
            video.play().catch(() => {});
        });
    }, []);

    const enableSound = () => {
        const video = videoRef.current;
        if (!video) return;

        video.muted = false;
        video.volume = 1;
        void video.play();
        setNeedsTapForSound(false);
    };

    return (
        <main className="alternate-path">
            <div className="alternate-path-card card">
                <Heading level={2}>{title}</Heading>

                {videoSrc ? (
                    <>
                        <video
                            ref={videoRef}
                            className="alternate-path-card__video"
                            src={videoSrc}
                            controls
                            playsInline
                            onVolumeChange={(e) => {
                                if (!e.currentTarget.muted) setNeedsTapForSound(false);
                            }}
                        />
                        {needsTapForSound && (
                            <Button onClick={enableSound}>🔊 Tap for sound</Button>
                        )}
                    </>
                ) : (
                    <p className="meta">
                        Add a video at <code>{expectedFile}</code> to play it here.
                    </p>
                )}

                <Button variant="link" onClick={() => navigate("/login")}>
                    Back to login
                </Button>
            </div>
        </main>
    );
}
