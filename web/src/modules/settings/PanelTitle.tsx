import { createContext, useContext, type ReactNode } from "react";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";

/** Set by SettingsSectionPage (value = the ready-made back IconButton) for sections whose panel
 * renders its own title. Null outside that page, e.g. when the panel is reused in a setup wizard. */
export const SectionBackContext = createContext<ReactNode | null>(null);

/** A panel's title. On a Settings section page it carries the page's back arrow immediately to its
 * left (and is the page's level-1 heading); anywhere else it is a plain h6. */
export function PanelTitle({ children }: { children: ReactNode }) {
	const backNode = useContext(SectionBackContext);
	if (backNode === null) {
		return <Typography variant="h6">{children}</Typography>;
	}
	return (
		<Stack direction="row" spacing={0.5} sx={{ alignItems: "center", ml: -1 }}>
			{backNode}
			<Typography variant="h6" component="h1">
				{children}
			</Typography>
		</Stack>
	);
}
