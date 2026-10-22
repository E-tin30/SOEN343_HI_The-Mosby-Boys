import "./Heading.css";

type HeadingProps = {
    level?: 1 | 2 | 3;
    className?: string;
    underline?: boolean;
    underlineText?: boolean;
    underlineAccent?: boolean;
    // Wrap text in tildes to make it the italic Tomato accent: "Dinner, ~sorted.~"
    children: string;
};

export default function Heading({
    level = 1,
    className = "",
    underline = false,
    underlineText = false,
    underlineAccent = false,
    children,
}: HeadingProps) {
    const Tag = `h${level}` as const;
    const match = children.match(/^(.*?)~(.+?)~(.*)$/);
    const prefix = match?.[1].replace(/\s+$/, "") ?? "";
    const prefixSpacing = match?.[1].slice(prefix.length) || (prefix ? " " : "");
    const headingClassName = [
        "heading",
        `heading--${level}`,
        underline && "heading--underline",
        className,
    ]
        .filter(Boolean)
        .join(" ");
    const textClassName = underlineText ? "heading__text--underline" : undefined;
    const accentClassName = [
        "heading__accent",
        underlineAccent && "heading__accent--underline",
    ]
        .filter(Boolean)
        .join(" ");

    return (
        <Tag className={headingClassName}>
            {match ? (
                <>
                    <span className={textClassName}>{prefix}</span>
                    {prefixSpacing}
                    <em className={accentClassName}>{match[2]}</em>
                    <span className={textClassName}>{match[3]}</span>
                </>
            ) : (
                <span className={textClassName}>{children}</span>
            )}
        </Tag>
    );
}
