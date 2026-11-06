package com.aidisciple.domain.material.logic;

/** 추출한 글 한 덩어리. PDF 는 쪽마다 하나, 붙여넣은 글은 {@code pageNo} 가 null 인 하나다. */
public record ExtractedPage(Integer pageNo, String text) {
}
