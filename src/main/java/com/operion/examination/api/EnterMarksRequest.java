package com.operion.examination.api;

import java.util.List;

public record EnterMarksRequest(List<MarkEntry> marks) {
}
