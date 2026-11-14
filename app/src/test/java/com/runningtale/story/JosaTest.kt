package com.runningtale.story

import org.junit.Assert.assertEquals
import org.junit.Test

class JosaTest {

    private fun fill(text: String, name: String) = Josa.fill(text, "{주인공}", name)

    @Test
    fun `받침 있는 이름`() {
        assertEquals("하윤은 웃었어요.", fill("{주인공}는 웃었어요.", "하윤"))
        assertEquals("하윤이 왔어요.", fill("{주인공}가 왔어요.", "하윤"))
        assertEquals("하윤을 불렀어요.", fill("{주인공}를 불렀어요.", "하윤"))
        assertEquals("하윤과 통통이", fill("{주인공}와 통통이", "하윤"))
        assertEquals("하윤으로 정했어요", fill("{주인공}로 정했어요", "하윤"))
        assertEquals("하윤아, 가자!", fill("{주인공}야, 가자!", "하윤"))
    }

    @Test
    fun `받침 없는 이름`() {
        assertEquals("지우는 통통이 위에 폴짝 올라탔어요.", fill("{주인공}은 통통이 위에 폴짝 올라탔어요.", "지우"))
        assertEquals("지우가 왔어요.", fill("{주인공}이 왔어요.", "지우"))
        assertEquals("지우를 불렀어요.", fill("{주인공}을 불렀어요.", "지우"))
        assertEquals("지우와 통통이", fill("{주인공}과 통통이", "지우"))
        assertEquals("지우로 정했어요", fill("{주인공}으로 정했어요", "지우"))
        assertEquals("지우야, 가자!", fill("{주인공}아, 가자!", "지우"))
    }

    @Test
    fun `ㄹ 받침은 으로가 아니라 로`() {
        assertEquals("하늘로 갔어요", fill("{주인공}으로 갔어요", "하늘"))
        assertEquals("하늘은 웃었어요", fill("{주인공}는 웃었어요", "하늘"))
    }

    @Test
    fun `조사 아닌 경우와 문장부호는 그대로`() {
        assertEquals("지우이에요.", fill("{주인공}이에요.", "지우"))
        assertEquals("다 왔어, 지우!", fill("다 왔어, {주인공}!", "지우"))
        assertEquals("지우의 머리카락", fill("{주인공}의 머리카락", "지우"))
        assertEquals("지우", fill("{주인공}", "지우"))
        assertEquals("지우, 지우는", fill("{주인공}, {주인공}은", "지우"))
    }

    @Test
    fun `한글로 끝나지 않는 이름은 조사를 바꾸지 않음`() {
        assertEquals("Amy은", fill("{주인공}은", "Amy"))
    }
}
