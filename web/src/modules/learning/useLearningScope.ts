import { useEffect, useState } from "react";
import { ApiError } from "../../api/client";
import { getLearningScope, type LearningScope } from "../../api/learning";

export function useLearningScope() {
	const [scope, setScope] = useState<LearningScope | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getLearningScope()
			.then(setScope)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load your sections"));
	}, []);

	return { scope, error };
}
