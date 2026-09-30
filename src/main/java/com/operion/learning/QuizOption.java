package com.operion.learning;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "quiz_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizOption extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "question_id")
	private QuizQuestion question;

	@Column(nullable = false)
	private int ordinal;

	@Column(name = "option_text", nullable = false)
	private String text;

	@Column(nullable = false)
	private boolean correct;

	public QuizOption(QuizQuestion question, int ordinal, String text, boolean correct) {
		this.question = question;
		this.ordinal = ordinal;
		this.text = text;
		this.correct = correct;
	}
}
