import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import { ApiError } from "../../api/client";
import { getChildMaterials, type MaterialResponse } from "../../api/learning";
import { MaterialAction, MaterialSummary } from "./MaterialList";

/** Read-only browse/download of the materials shared with the child's class (#228). */
export function ChildMaterialsPanel({ studentId }: { studentId: number }) {
	const [items, setItems] = useState<MaterialResponse[] | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getChildMaterials(studentId)
			.then(setItems)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load materials"));
	}, [studentId]);

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}
			{items?.length === 0 && <Alert severity="info">No materials have been shared yet.</Alert>}
			{items?.map((m) => (
				<Paper key={m.id} sx={{ p: 2 }}>
					<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center" }} spacing={2}>
						<MaterialSummary material={m} />
						<MaterialAction material={m} />
					</Stack>
				</Paper>
			))}
		</Stack>
	);
}
