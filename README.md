# 🍳 Cookbook (레시피 북)

GitHub Markdown 기반의 스마트 쿡북입니다.  
`/recipe` 폴더 하위에 마크다운(`.md`) 파일로 새 레시피를 추가하면, 웹 페이지가 GitHub API를 통해 실시간으로 목록을 긁어와 자동으로 카드를 생성합니다!

---

## 📂 디렉토리 구조

```text
cookbook/
├── index.html                  # GitHub Pages 진입점 (html/index.html로 리다이렉트)
├── html/
│   └── index.html              # 레시피 웹 뷰어 (GitHub Tree API 기반 실시간 연동)
└── recipe/                     # 레시피 마크다운 모음
    ├── korean/                 # 한식 카테고리
    │   ├── RECIPE_TTEOKBOKKI.md
    │   └── RECIPE_GODEUNGEO_GUI.md
    ├── western/                # 양식 카테고리 (예시)
    ├── japanese/               # 일식 카테고리 (예시)
    └── dessert/                # 디저트 카테고리 (예시)
```

---

## 📝 새 레시피 추가 방법

`recipe/<카테고리명>/` 폴더 아래에 새 `.md` 파일을 만들기만 하면 됩니다!  
코드(`index.html`)를 직접 건드릴 필요가 전혀 없습니다.

### 지원 카테고리 폴더명 예시
- `korean`: 한식
- `western`: 양식
- `japanese`: 일식
- `chinese`: 중식
- `dessert`: 디저트/베이킹
- `snack`: 분식/간식
- 기타 원하는 영문 폴더명 자유 사용 가능

### 레시피 작성 템플릿 예시

```markdown
# 🥘 레시피 제목

간단한 한 줄 소개글입니다.

---

## 📌 개요
* **조리 분량:** 2인분 기준
* **조리 시간:** 약 20분
* **난이도:** 쉬움

---

## 🛒 재료 (2인분)
| 재료 | 수량 / 분량 | 비고 |
| :--- | :--- | :--- |
| **재료1** | 300g | 설명 |
| **재료2** | 2큰술 | 설명 |

---

## 🍳 단계별 레시피 (Instructions)

### Step 1. 재료 밑준비
1. 첫 번째 순서 설명...

### Step 2. 조리하기
1. 두 번째 순서 설명...
```
