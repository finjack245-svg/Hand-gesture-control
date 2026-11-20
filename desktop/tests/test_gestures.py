import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app import classify_hand


class Landmark:
    def __init__(self, x, y):
        self.x = x
        self.y = y


def hand(up):
    points = [Landmark(0.5, 0.5) for _ in range(21)]
    for tip, pip in ((8, 6), (12, 10), (16, 14), (20, 18)):
        points[tip].y = 0.2 if up.get(tip, False) else 0.8
        points[pip].y = 0.5
    points[4].x, points[4].y = 0.1, 0.1
    points[8].x = 0.9  # keep thumb-index distance outside pinch threshold
    return points


class GestureClassifierTests(unittest.TestCase):
    def test_open_palm(self):
        self.assertEqual(classify_hand(hand({8: True, 12: True, 16: True, 20: True})), "OPEN_PALM")

    def test_fist(self):
        self.assertEqual(classify_hand(hand({})), "FIST")

    def test_peace(self):
        self.assertEqual(classify_hand(hand({8: True, 12: True})), "PEACE")

    def test_point(self):
        self.assertEqual(classify_hand(hand({8: True})), "POINT")

    def test_three_fingers(self):
        self.assertEqual(classify_hand(hand({8: True, 12: True, 16: True})), "THREE_FINGERS")


if __name__ == "__main__":
    unittest.main()
