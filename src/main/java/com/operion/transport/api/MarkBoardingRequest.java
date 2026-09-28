package com.operion.transport.api;

import java.util.List;

public record MarkBoardingRequest(List<BoardingMarkEntry> marks) {
}
